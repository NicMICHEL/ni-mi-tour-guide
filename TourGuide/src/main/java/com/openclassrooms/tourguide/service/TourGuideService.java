package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class TourGuideService {
    private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();
    public final Tracker tracker;
    private final ExecutorService executorService = Executors.newFixedThreadPool(200);
    boolean testMode = true;

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

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
        VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
                : trackUserLocation(user);
        return visitedLocation;
    }

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    public List<User> getAllUsers() {
        return internalUserMap.values().stream().collect(Collectors.toList());
    }

    public void addUser(User user) {
        if (!internalUserMap.containsKey(user.getUserName())) {
            internalUserMap.put(user.getUserName(), user);
        }
    }

    public List<Provider> getTripDeals(User user) {
        int cumulativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
        List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(), cumulativeRewardPoints);
        user.setTripDeals(providers);
        return providers;
    }

    public VisitedLocation trackUserLocation(User user) {
        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(visitedLocation);
        rewardsService.calculateRewards(user);
        return visitedLocation;
    }

    public CompletableFuture<VisitedLocation> trackUserLocation2(User user) {
        return CompletableFuture.supplyAsync(() -> {
            VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
            user.addToVisitedLocations(visitedLocation);
            rewardsService.calculateRewards(user);
            return visitedLocation;
        }, executorService);
    }

    public void trackAllUsersLocation(List<User> allUsers) throws ExecutionException, InterruptedException {
        List<CompletableFuture<VisitedLocation>> completableFutureList
                = allUsers.stream().map(this::trackUserLocation2).toList();
        completableFutureList.forEach(CompletableFuture::join);
    }

    //gives the list of the 5 attractions nearest to visitedLocation
    public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
        List<Attraction> nearbyAttractions = new ArrayList<>();
        Attraction[] nearestAttractions;
        nearestAttractions = new Attraction[5];
        //Before starting the tests, the table nearestDistances is filled with 5 distances ranked in ascending order.
        // Each of these distances is greater than the maximum distance between any two points on the earth's surface.
        double[] nearestDistances = {25000.1, 25000.2, 25000.3, 25000.4, 25000.5};
        for (Attraction attraction : gpsUtil.getAttractions()) {
            double distanceCandidateAttraction;
            distanceCandidateAttraction = rewardsService.getDistance(attraction, visitedLocation.location);
            if (distanceCandidateAttraction < nearestDistances[2]) {
                if (distanceCandidateAttraction < nearestDistances[0]) {
                    moveForwardAndInsert(distanceCandidateAttraction, 0, nearestDistances);
                    moveForwardAndInsert(attraction, 0, nearestAttractions);
                } else {
                    if (distanceCandidateAttraction < nearestDistances[1]) {
                        moveForwardAndInsert(distanceCandidateAttraction, 1, nearestDistances);
                        moveForwardAndInsert(attraction, 1, nearestAttractions);
                    } else {
                        moveForwardAndInsert(distanceCandidateAttraction, 2, nearestDistances);
                        moveForwardAndInsert(attraction, 2, nearestAttractions);
                    }
                }
            } else {
                if (distanceCandidateAttraction < nearestDistances[4]) {
                    if (distanceCandidateAttraction < nearestDistances[3]) {
                        moveForwardAndInsert(distanceCandidateAttraction, 3, nearestDistances);
                        moveForwardAndInsert(attraction, 3, nearestAttractions);
                    } else {
                        moveForwardAndInsert(distanceCandidateAttraction, 4, nearestDistances);
                        moveForwardAndInsert(attraction, 4, nearestAttractions);
                    }
                }
            }
        }
        nearbyAttractions.addAll(Arrays.asList(nearestAttractions));
        return nearbyAttractions;
    }

    private Attraction[] moveForwardAndInsert(Attraction attraction, int i, Attraction[] attractions) {
        for (int j = 4; j > i; j--) {
            attractions[j] = attractions[j - 1];
        }
        attractions[i] = attraction;
        return attractions;
    }

    private double[] moveForwardAndInsert(double distance, int i, double[] distances) {
        for (int j = 4; j > i; j--) {
            distances[j] = distances[j - 1];
        }
        distances[i] = distance;
        return distances;
    }

    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                tracker.stopTracking();
            }
        });
    }

    /**********************************************************************************
     *
     * Methods Below: For Internal Testing
     *
     **********************************************************************************/
    private static final String tripPricerApiKey = "test-server-api-key";
    // Database connection will be used for external users, but for testing purposes
    // internal users are provided and stored in memory
    private final Map<String, User> internalUserMap = new HashMap<>();

    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";
            User user = new User(UUID.randomUUID(), userName, phone, email);
            generateUserLocationHistory(user);

            internalUserMap.put(userName, user);
        });
        logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
        });
    }

    private double generateRandomLongitude() {
        double leftLimit = -180;
        double rightLimit = 180;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

}
