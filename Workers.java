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
    volatile boolean running;

    public Workers(int numOfThreads) {
       this.numOfThreads = numOfThreads;
       this.tasks = new LinkedList<>();
       this.threads =  new LinkedList<>();
       this.lock = new ReentrantLock();
       this.taskAvailable = lock.newCondition();
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

    //Will finish tasks in task lists  before stopping
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

    public static void main(String[] args)  {
        Workers workerThreads = new Workers(4);
        Workers eventLoop = new Workers(1);

        workerThreads.start();
        eventLoop.start();

        workerThreads.postTimeout(() -> {
            System.out.println("Task A"); // Task A
        }, 3000);

        workerThreads.post(() -> {
            System.out.println("Task B"); // Task B  // Might run in parallel with task A

        });

        /*       workerThreads.postTimeout(() -> {
            System.out.println("Task B"); // Task B wit wait timer  // Might run in parallel with task A

        }, 3000);*/

        eventLoop.post(() -> {
            System.out.println("Task C"); // Task C // Might run in parallel with task A and B
        });


    /*    workerThreads.stop();
        eventLoop.stop();*/



        eventLoop.post(() -> {
            System.out.println("Task D"); // Task D // Will run after task C // Might run in parallel with task A and B
        });


        workerThreads.join();
        eventLoop.join();
    }
}


