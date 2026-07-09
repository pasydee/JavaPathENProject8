package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {

    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

    private int defaultProximityBuffer = 10;
    private int proximityBuffer = defaultProximityBuffer;
    private int attractionProximityRange = 200;

    private final GpsUtil gpsUtil;
    private final RewardCentral rewardsCentral;
    private final List<Attraction> attractions;

    private final ConcurrentHashMap<String, Double> distanceCache = new ConcurrentHashMap<>();

    public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
        this.gpsUtil = gpsUtil;
        this.rewardsCentral = rewardCentral;
        this.attractions = gpsUtil.getAttractions();
    }

    public void calculateRewards(User user) {

        Set<UUID> rewardedAttractions = user.getUserRewards()
                .stream()
                .map(r -> r.attraction.attractionId)
                .collect(Collectors.toSet());

        List<VisitedLocation> visitedLocationsCopy = new ArrayList<>(user.getVisitedLocations());

        for (VisitedLocation visitedLocation : visitedLocationsCopy) {

            for (Attraction attraction : attractions) {

                if (rewardedAttractions.contains(attraction.attractionId)) continue;

                if (nearAttraction(visitedLocation, attraction)) {
                    int rewardPoints = getRewardPoints(attraction, user);
                    user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
                    rewardedAttractions.add(attraction.attractionId);
                }
            }
        }
    }

    private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
        return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
    }

    public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
        return getDistance(attraction, location) <= attractionProximityRange;
    }

    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    public void setDefaultProximityBuffer() {
        this.proximityBuffer = defaultProximityBuffer;
    }

    public double getDistance(Location loc1, Location loc2) {

        String key = loc1.latitude + "_" + loc1.longitude + "_" + loc2.latitude + "_" + loc2.longitude;

        Double cached = distanceCache.get(key);
        if (cached != null) return cached;

        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;

        distanceCache.put(key, statuteMiles);

        return statuteMiles;
    }

    public int getRewardPoints(Attraction attraction, User user) {
        return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
    }
}

