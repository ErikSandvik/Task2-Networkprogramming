import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Workers {
    int numOfThreads;
    Queue<Runnable> tasks;
    List<Thread> threads;
    Lock lock;
    Condition taskAvailable;
    boolean running;

    public Workers(int numOfThreads) {
       this.numOfThreads = numOfThreads;
       this.tasks = new LinkedList<>();
       this.threads =  new LinkedList<>();
       this.taskAvailable = lock.newCondition();
       this.lock = new ReentrantLock();
       this.running = false;
    }

    public void start() {
        if (!running) {
            running = true;

            for (int i = 0; i < numOfThreads; i++) {
                Thread workerThread = new Thread(() -> {
                    while (running || !tasks.isEmpty()) {
                        try {
                            lock.lock();
                            while (tasks.isEmpty() && running) {
                                taskAvailable.await();
                            }

                            if (!tasks.isEmpty()) {
                                Runnable task = tasks.remove();
                                task.run();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            lock.unlock();
                        }
                    }
                });

                threads.add(workerThread);
                workerThread.start();
            }
        }
    }

    public void stop() {
        running = false;
        lock.lock();
        taskAvailable.signalAll();  // Wake up all threads to check the running flag
        lock.unlock();

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void post(Runnable task) {
        if (running) {
            lock.lock();
            tasks.add(task);
            taskAvailable.signal();
            lock.unlock();
        }
    }

    public void postTimeout(Runnable task, long waitingTime) {
        if (running) {
            Runnable delayedTask = () -> {
                try {
                    Thread.sleep(waitingTime);
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            post(delayedTask);
        }
    }

    public void join() {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        Workers workerThreads = new Workers(4);
        Workers eventLoop = new Workers(1);

        workerThreads.start();
        eventLoop.start();

        workerThreads.post(() -> {
            System.out.println("Task A");
        });

        workerThreads.post(() -> {
            System.out.println("Task B");
        });

        eventLoop.post(() -> {
            System.out.println("Task C");
        });

        eventLoop.post(() -> {
            System.out.println("Task D");
        });

        workerThreads.join();
        eventLoop.join();
    }
}


