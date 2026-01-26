package com.allan.androidlearning.picwall;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LIFO（后进先出）线程池工具类（支持自动重建）
 * 核心特性：
 * 1. 可控制并发线程数（固定线程池）
 * 2. 任务执行顺序：后提交的任务优先执行（LIFO）
 * 3. 线程池关闭后，提交任务时自动重建（保留原配置）
 * 4. 完善的线程安全和异常处理
 */
public class LIFOThreadPoolUtil {
    // 核心线程池实例（volatile保证多线程可见性）
    private volatile ThreadPoolExecutor lifoExecutor;
    // 线程池是否已关闭的标记
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    
    // 保存创建线程池的核心配置（用于重建）
    private final int corePoolSize;
    private final int queueCapacity;
    private final RejectedExecutionHandler rejectedHandler;

    /**
     * 私有构造器（通过静态工厂方法创建实例）
     * @param corePoolSize 核心线程数（并发数，固定值）
     * @param queueCapacity 任务队列容量（0表示无界队列）
     * @param rejectedExecutionHandler 拒绝策略
     */
    private LIFOThreadPoolUtil(int corePoolSize, int queueCapacity, RejectedExecutionHandler rejectedExecutionHandler) {
        // 校验参数合法性
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("核心线程数必须大于0");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("队列容量不能为负数");
        }
        
        // 保存配置参数（用于重建）
        this.corePoolSize = corePoolSize;
        this.queueCapacity = queueCapacity;
        this.rejectedHandler = rejectedExecutionHandler != null ? rejectedExecutionHandler : new ThreadPoolExecutor.AbortPolicy();
        
        // 初始化线程池
        this.lifoExecutor = createLIFOExecutor();
    }

    // ====================== 核心：创建/重建线程池的私有方法 ======================
    /**
     * 创建LIFO线程池（复用配置，供初始化和重建调用）
     */
    private ThreadPoolExecutor createLIFOExecutor() {
        // 1. 创建LIFO队列（后提交任务插入队列头部）
        BlockingQueue<Runnable> lifoQueue;
        if (queueCapacity == 0) {
            // 无界队列
            lifoQueue = new LinkedBlockingDeque<Runnable>() {
                @Override
                public boolean offer(Runnable e) {
                    return super.offerFirst(e); // LIFO核心：插入头部
                }
            };
        } else {
            // 有界队列
            lifoQueue = new LinkedBlockingDeque<Runnable>(queueCapacity) {
                @Override
                public boolean offer(Runnable e) {
                    return super.offerFirst(e);
                }
            };
        }

        // 2. 构建线程池
        return new ThreadPoolExecutor(
                corePoolSize,
                corePoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                lifoQueue,
                Executors.defaultThreadFactory(),
                this.rejectedHandler
        );
    }

    /**
     * 线程安全的线程池重建方法（双重检查锁，避免重复创建）
     */
    private void rebuildExecutorIfShutdown() {
        // 第一次检查：无锁，快速判断（大部分情况不走重建逻辑）
        if (isShutdown.get()) {
            synchronized (this) {
                // 第二次检查：加锁后再次确认，避免并发重建
                if (isShutdown.get()) {
                    // 1. 重置关闭标记
                    isShutdown.set(false);
                    // 2. 重建线程池（复用原配置）
                    this.lifoExecutor = createLIFOExecutor();
                    System.out.println("线程池已重建，配置：并发数=" + corePoolSize + "，队列容量=" + (queueCapacity == 0 ? "无界" : queueCapacity));
                }
            }
        }
    }

    // ====================== 静态工厂方法 ======================
    /**
     * 创建无界队列的LIFO固定线程池（最常用）
     * @param corePoolSize 核心线程数（并发数）
     * @return LIFOThreadPoolUtil实例
     */
    public static LIFOThreadPoolUtil createFixedLIFOThreadPool(int corePoolSize) {
        return new LIFOThreadPoolUtil(corePoolSize, 0, null);
    }

    /**
     * 创建有界队列的LIFO固定线程池（防止任务堆积）
     * @param corePoolSize 核心线程数（并发数）
     * @param queueCapacity 队列最大容量
     * @return LIFOThreadPoolUtil实例
     */
    public static LIFOThreadPoolUtil createFixedLIFOThreadPool(int corePoolSize, int queueCapacity) {
        return new LIFOThreadPoolUtil(corePoolSize, queueCapacity, null);
    }

    /**
     * 创建自定义拒绝策略的LIFO固定线程池
     * @param corePoolSize 核心线程数（并发数）
     * @param queueCapacity 队列容量（0=无界）
     * @param rejectedHandler 拒绝策略
     * @return LIFOThreadPoolUtil实例
     */
    public static LIFOThreadPoolUtil createFixedLIFOThreadPool(int corePoolSize, int queueCapacity, RejectedExecutionHandler rejectedHandler) {
        return new LIFOThreadPoolUtil(corePoolSize, queueCapacity, rejectedHandler);
    }

    // ====================== 任务提交方法（核心改造） ======================
    /**
     * 提交Runnable任务（自动检测并重建关闭的线程池）
     * @param task 待执行的任务
     * @throws NullPointerException 任务为null时抛出
     */
    public void execute(@NonNull Runnable task) {
        // 步骤1：检测线程池是否关闭，若关闭则重建
        rebuildExecutorIfShutdown();
        
        // 步骤2：提交任务（此时线程池已保证可用）
        lifoExecutor.execute(task);
    }

    /**
     * 提交Callable任务（支持返回值，自动重建）
     * @param task 待执行的Callable任务
     * @return Future对象
     * @throws NullPointerException 任务为null时抛出
     */
    public <T> Future<T> submit(@NonNull Callable<T> task) {
        rebuildExecutorIfShutdown();
        return lifoExecutor.submit(task);
    }

    /**
     * 提交Runnable任务（支持返回值，自动重建）
     * @param task 待执行的Runnable任务
     * @param result 任务执行完成后的返回值
     * @return Future对象
     */
    public <T> Future<T> submit(@NonNull Runnable task, T result) {
        rebuildExecutorIfShutdown();
        return lifoExecutor.submit(task, result);
    }

    // ====================== 线程池关闭方法 ======================
    /**
     * 优雅关闭线程池（等待已提交任务执行完成，拒绝新任务）
     */
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            lifoExecutor.shutdown();
            System.out.println("线程池已优雅关闭");
        }
    }

    /**
     * 强制关闭线程池（立即中断所有任务，返回未执行的任务列表）
     * @return 未执行的任务列表
     */
    public List<Runnable> shutdownNow() {
        if (isShutdown.compareAndSet(false, true)) {
            List<Runnable> unexecutedTasks = lifoExecutor.shutdownNow();
            System.out.println("线程池已强制关闭，未执行任务数：" + unexecutedTasks.size());
            return unexecutedTasks;
        }
        return java.util.Collections.emptyList();
    }

    // ====================== 状态查询方法 ======================
    /**
     * 检查线程池是否已关闭
     * @return true=已关闭
     */
    public boolean isShutdown() {
        return isShutdown.get();
    }

    /**
     * 检查线程池是否已终止（所有任务执行完成且已关闭）
     * @return true=已终止
     */
    public boolean isTerminated() {
        return lifoExecutor.isTerminated();
    }

    /**
     * 获取当前活跃线程数
     * @return 活跃线程数
     */
    public int getActiveCount() {
        rebuildExecutorIfShutdown(); // 防止获取状态时线程池已关闭
        return lifoExecutor.getActiveCount();
    }
}