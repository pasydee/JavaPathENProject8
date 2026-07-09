package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {

    private Logger logger = LoggerFactory.getLogger(TourGuideService.class);

    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();
    public final Tracker tracker;

    private static final String tripPricerApiKey = "test-server-api-key";
    private final Map<String, User> internalUserMap = new HashMap<>();

    private final List<Attraction> attractions;

    boolean testMode = true;

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

        this.attractions = gpsUtil.getAttractions();

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");
            initializeInternalUsers();
            logger.debug("Finished initializing users");
        }

        tracker = new Tracker(this);
        addShutDownHook();
    }

    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    public VisitedLocation getUserLocation(User user) {
        return user.getVisitedLocations().isEmpty()
                ? trackUserLocation(user)
                : user.getLastVisitedLocation();
    }

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(internalUserMap.values());
    }

    public void addUser(User user) {
        internalUserMap.putIfAbsent(user.getUserName(), user);
    }

    public List<Provider> getTripDeals(User user) {
        int cumulativeRewardPoints = user.getUserRewards()
                .stream()
                .mapToInt(UserReward::getRewardPoints)
                .sum();

        List<Provider> providers = tripPricer.getPrice(
                tripPricerApiKey,
                user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(),
                user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(),
                cumulativeRewardPoints
        );

        user.setTripDeals(providers);
        return providers;
    }

    // ⭐ Version propre et synchrone
    public VisitedLocation trackUserLocation(User user) {
        try {
            VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());

            user.addToVisitedLocations(visitedLocation);

            rewardsService.calculateRewards(user);

            return visitedLocation;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {

        Location userLocation = visitedLocation.location;

        return attractions.stream()
                .map(a -> Map.entry(a, rewardsService.getDistance(userLocation, a)))
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<NearbyAttractionDTO> getNearbyAttractionsDTO(String userName) {

        User user = getUser(userName);
        VisitedLocation visitedLocation = getUserLocation(user);
        Location userLocation = visitedLocation.location;

        // On utilise TA méthode existante, intacte
        List<Attraction> nearby = getNearByAttractions(visitedLocation);

        return nearby.stream()
                .map(a -> new NearbyAttractionDTO(
                        a.attractionName,
                        a.latitude,
                        a.longitude,
                        userLocation.latitude,
                        userLocation.longitude,
                        rewardsService.getDistance(userLocation, a),
                        rewardsService.getRewardPoints(a, user)
                ))
                .collect(Collectors.toList());
    }


    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> tracker.stopTracking()));
    }

    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            User user = new User(UUID.randomUUID(), userName, "000", userName + "@tourGuide.com");
            generateUserLocationHistory(user);
            internalUserMap.put(userName, user);
        });
        logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(
                    user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()),
                    getRandomTime()
            ));
        });
    }

    private double generateRandomLongitude() {
        return -180 + new Random().nextDouble() * 360;
    }

    private double generateRandomLatitude() {
        return -85.05112878 + new Random().nextDouble() * (85.05112878 * 2);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }
}
