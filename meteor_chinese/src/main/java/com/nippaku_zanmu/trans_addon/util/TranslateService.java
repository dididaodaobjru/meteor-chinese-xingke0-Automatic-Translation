package com.nippaku_zanmu.trans_addon.util;

import meteordevelopment.meteorclient.MeteorClient;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台翻译服务。
 * <p>
 * 所有翻译请求都排进同一条队列，由单个守护线程串行处理：一来避免并发打爆免费接口的
 * 频率限制，二来保证聊天消息的翻译顺序与原文一致。回调统一切回 Minecraft 主线程执行。
 */
public final class TranslateService {
    /** 单条翻译任务完成后的回调，运行在 Minecraft 主线程。 */
    public interface Callback {
        void done(@Nullable String translated, @Nullable String lang, boolean ok);
    }

    public record Task(String text, String targetLang, String sourceLang, TranslateSource source,
                       String apiUrl, String apiKey, String model, Callback callback) {}

    private static final BlockingQueue<Task> QUEUE = new LinkedBlockingQueue<>();
    private static final AtomicInteger PENDING = new AtomicInteger();

    private static final long REQUEST_INTERVAL_MS = 180;

    private static volatile Thread worker;
    private static volatile Runnable onIdle;
    private static volatile String lastError;

    private TranslateService() {}

    /** 队列排空时在主线程回调一次，用于触发界面文本的重新应用。 */
    public static void setOnIdle(Runnable runnable) {
        onIdle = runnable;
    }

    public static boolean isBusy() {
        return PENDING.get() > 0;
    }

    public static int pendingCount() {
        return PENDING.get();
    }

    @Nullable
    public static String lastError() {
        return lastError;
    }

    public static void submit(Task task) {
        if (task == null || task.text() == null || task.text().isBlank()) return;

        PENDING.incrementAndGet();
        QUEUE.add(task);
        ensureWorker();
    }

    private static synchronized void ensureWorker() {
        if (worker != null && worker.isAlive()) return;

        worker = new Thread(TranslateService::loop, "meteor-chinese-translator");
        worker.setDaemon(true);
        worker.start();
    }

    private static void loop() {
        while (true) {
            Task task;

            try {
                task = QUEUE.take();
            } catch (InterruptedException e) {
                return;
            }

            String translated = null;
            String lang = null;
            boolean ok = false;

            try {
                TranslateApi.Result result = TranslateApi.translate(
                    task.text(), task.targetLang(), task.sourceLang(), task.source(),
                    task.apiUrl(), task.apiKey(), task.model());

                translated = result.text();
                lang = result.lang();
                ok = translated != null && !translated.isBlank();
                lastError = null;
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }

            PENDING.decrementAndGet();

            final String fTranslated = ok ? translated : null;
            final String fLang = lang;
            final boolean fOk = ok;

            runOnClient(() -> task.callback().done(fTranslated, fLang, fOk));

            if (PENDING.get() == 0) {
                Runnable idle = onIdle;
                if (idle != null) runOnClient(idle);
            }

            try {
                Thread.sleep(REQUEST_INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void runOnClient(Runnable runnable) {
        try {
            if (MeteorClient.mc != null) MeteorClient.mc.execute(runnable);
            else runnable.run();
        } catch (Exception e) {
            runnable.run();
        }
    }
}
