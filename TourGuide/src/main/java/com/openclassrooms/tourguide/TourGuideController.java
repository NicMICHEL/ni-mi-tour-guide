package com.openclassrooms.tourguide;

import com.openclassrooms.tourguide.dto.AttractionNearTheUser;
import com.openclassrooms.tourguide.dto.NearByAttractionsInfos;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tripPricer.Provider;

import java.util.ArrayList;
import java.util.List;

@RestController
public class TourGuideController {

    @Autowired
    TourGuideService tourGuideService;
    @Autowired
    RewardsService rewardsService;

    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        return tourGuideService.getUserLocation(getUser(userName));
    }

    /*
    //  TODO: Change this method to no longer return a List of Attractions.
 	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
 	//  Return a new JSON object that contains:
    	// Name of Tourist attraction, 
        // Tourist attractions lat/long, 
        // The user's location lat/long, 
        // The distance in miles between the user's location and each of the attractions.
        // The reward points for visiting each Attraction.
        //    Note: Attraction reward points can be gathered from RewardsCentral
    @RequestMapping("/getNearbyAttractions") 
    public List<Attraction> getNearbyAttractions(@RequestParam String userName) {
    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
    	return tourGuideService.getNearByAttractions(visitedLocation);
    }
     */

    @RequestMapping("/getNearbyAttractions")
    public NearByAttractionsInfos getNearbyAttractions(@RequestParam String userName) {
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
        List<Attraction> nearByAttractions = tourGuideService.getNearByAttractions(visitedLocation);
        List<AttractionNearTheUser> attractionsNearTheUser = new ArrayList<>();
        for (Attraction attraction : nearByAttractions) {
            AttractionNearTheUser attractionNearTheUser = new AttractionNearTheUser(
                    attraction.attractionName,
                    attraction.longitude,
                    attraction.latitude,
                    rewardsService.getDistance(attraction, visitedLocation.location),
                    rewardsService.getRewardPoints(attraction, getUser(userName)));
            attractionsNearTheUser.add(attractionNearTheUser);
        }
        return new NearByAttractionsInfos(
                visitedLocation.location.longitude,
                visitedLocation.location.latitude,
                attractionsNearTheUser);
    }

    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        return tourGuideService.getUserRewards(getUser(userName));
    }

    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        return tourGuideService.getTripDeals(getUser(userName));
    }

    private User getUser(String userName) {
        return tourGuideService.getUser(userName);
    }


}