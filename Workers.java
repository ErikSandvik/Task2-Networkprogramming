import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Workers {
    //List to store worker threads
    List<Thread> threads;

    //Queue of runnable tasks
    Queue<Runnable> tasks;

    //Lock and condition variables to avoid concurrency issues
    Lock lock;
    Condition taskAvailable;


    volatile boolean running;

    //Constructor that makes a specific number of threads
    public Workers(int numOfThreads) {
        threads = new LinkedList<>();
        tasks = new LinkedList<>();
        this.lock = new ReentrantLock();
        this.taskAvailable = lock.newCondition();
        this.running = false;

        //Creating the threads
        for (int i = 0; i < numOfThreads; i++) {
            Thread workerThread = new Thread(() -> {
                while (running || !tasks.isEmpty()) {
                    try {
                        Runnable task;
                        lock.lock();
                        try {
                            //If there are no tasks, then wait and wait for signal for a task being available
                            while (tasks.isEmpty() && running) {
                                taskAvailable.await();
                            }

                            //Retrieves a task from queue
                            if (!tasks.isEmpty()) {
                                task = tasks.remove();
                            } else {
                                //Terminates if there are no more tasks
                                break;
                            }
                        } finally {
                            lock.unlock();
                        }

                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            //Add the thread to the list of threads. Pool?
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

    //Change running boolean value to false, and then notify all threads waiting for signal from condition variable
    public void stop() {
        running = false;
        lock.lock();
        try {
            taskAvailable.signalAll();
        } finally {
            lock.unlock();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    //Posts a task to the available pool of tasks
    public void post(Runnable task) {
        if (running) {
            lock.lock();
            try {
                tasks.add(task);
                taskAvailable.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    //Adds a task that takes a delay in the run() method
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

    //Join all tasks in thread list
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


        /*     workerThreads.postTimeout(() -> {
            System.out.println("Task A"); // Task A
        }, 2000);*/

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


