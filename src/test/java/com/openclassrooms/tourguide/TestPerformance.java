package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class TestPerformance {

	/*
	 * A note on performance improvements:
	 * 
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 * 
	 * InternalTestHelper.setInternalUserNumber(100000);
	 * 
	 * 
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 * 
	 * These are performance metrics that we are trying to hit:
	 * 
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

    @Test
    public void trackUserLocation_parallel() throws Exception {

        GpsUtil gpsUtil = new GpsUtil();
        RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
        InternalTestHelper.setInternalUserNumber(100000);
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        List<User> users = tourGuideService.getAllUsers();

        ExecutorService executor = Executors.newFixedThreadPool(1000);

        List<Callable<VisitedLocation>> tasks = new ArrayList<>();

        for (User user : users) {
            tasks.add(() -> tourGuideService.trackUserLocation(user));
        }

        long start = System.currentTimeMillis();

        List<Future<VisitedLocation>> results = executor.invokeAll(tasks);

        long end = System.currentTimeMillis();

        System.out.println("Parallel test took: " + (end - start) + " ms");

        // Vérification : tous les users ont bien une location
        for (Future<VisitedLocation> f : results) {
            assertNotNull(f.get());
        }

        executor.shutdown();
        tourGuideService.tracker.stopTracking();
    }

    @Test
    public void highVolumeGetRewards() throws Exception {

        GpsUtil gpsUtil = new GpsUtil();
        RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

        InternalTestHelper.setInternalUserNumber(100000);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        Attraction attraction = gpsUtil.getAttractions().get(0);

        List<User> allUsers = tourGuideService.getAllUsers();

        allUsers.forEach(u ->
                u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()))
        );

        ExecutorService executor = Executors.newFixedThreadPool(1000);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (User user : allUsers) {
            tasks.add(() -> {
                rewardsService.calculateRewards(user);
                return null;
            });
        }

        executor.invokeAll(tasks);
        executor.shutdown();

        for (User user : allUsers) {
            assertTrue(user.getUserRewards().size() > 0);
        }

        stopWatch.stop();
        tourGuideService.tracker.stopTracking();

        System.out.println("highVolumeGetRewards: Time Elapsed: "
                + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");

        assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
    }
}
