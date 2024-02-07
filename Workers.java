import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Workers {
    List<Thread> threads;
    List<Runnable> tasks;
    Object lock;
    volatile boolean running;

    public Workers(int numOfThreads) {
        threads = new LinkedList<>();
        tasks = new LinkedList<>();
        this.lock = new Object();
        this.running = false;

        for (int i = 0; i < numOfThreads; i++) {
            Thread workerThread = new Thread(() -> {
                while (running || !tasks.isEmpty()) {
                    try {
                        Runnable task;
                        synchronized (lock) {
                            while (tasks.isEmpty() && running) {
                                lock.wait();
                            }
                            if (!tasks.isEmpty()) {
                                task = tasks.remove(0);
                            }
                            else {
                                break;
                            }
                        }

                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            threads.add(workerThread);
        }
    }

    public void start() {
        if (!running) {
            running = true;
            for (Thread thread : threads) {
                thread.start();
            }
        }
    }


    public void stop() {
        running = false;
        synchronized (lock) {
            lock.notifyAll();  // Wake up all threads to check the running flag
        }

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
            synchronized (lock) {
                tasks.add(task);
                lock.notify();  // Wake up one waiting thread to execute the task
            }
        }
    }

    public void postTimeout(Runnable task, long waitingTimeInMillis) {
        if (running) {
            Runnable delayedTask = () -> {
                try {
                    Thread.sleep(waitingTimeInMillis);
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
            System.out.println("Task A"); // Task A
        });

        workerThreads.post(() -> {
            System.out.println("Task B"); // Task B  // Might run in parallel with task A

        });

        eventLoop.post(() -> {
            System.out.println("Task C"); // Task C // Might run in parallel with task A and B
        });

        eventLoop.post(() -> {
            System.out.println("Task D"); // Task D // Will run after task C // Might run in parallel with task A and B
        });


        workerThreads.stop();
        eventLoop.stop();

        workerThreads.join();
        eventLoop.join();
    }
}


