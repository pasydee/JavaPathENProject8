package com.openclassrooms.tourguide.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class Tracker implements Runnable {

    // Intervalle entre deux cycles de tracking
    private static final long TRACKING_INTERVAL = TimeUnit.SECONDS.toMillis(5);

    // Pool de threads pour exécuter trackUserLocation en parallèle
    private final ExecutorService executorService = Executors.newFixedThreadPool(1000);

    private final TourGuideService tourGuideService;
    private final Thread trackerThread;
    private volatile boolean stop = false;

    public Tracker(TourGuideService tourGuideService) {
        this.tourGuideService = tourGuideService;
        this.trackerThread = new Thread(this);
        this.trackerThread.setDaemon(true);
        this.trackerThread.start();
    }

    @Override
    public void run() {
        while (!stop) {
            try {
                List<User> users = tourGuideService.getAllUsers();

                List<Callable<Void>> tasks = new ArrayList<>(users.size());

                for (User user : users) {
                    tasks.add(() -> {
                        tourGuideService.trackUserLocation(user);
                        return null;
                    });
                }

                // Exécuter toutes les tâches en parallèle
                executorService.invokeAll(tasks);

                Thread.sleep(TRACKING_INTERVAL);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopTracking() {
        stop = true;
        executorService.shutdownNow();

        try {
            executorService.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }
}
