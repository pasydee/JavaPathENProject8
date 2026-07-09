package com.openclassrooms.tourguide;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TourGuideController.class)
public class TourGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TourGuideService tourGuideService;

    private User user;

    @BeforeEach
    void setup() {
        user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
    }

    @Test
    void testGetLocation() throws Exception {
        when(tourGuideService.getUser("jon")).thenReturn(user);
        when(tourGuideService.getUserLocation(user)).thenReturn(
                new gpsUtil.location.VisitedLocation(
                        user.getUserId(),
                        new gpsUtil.location.Location(10.0, 20.0),
                        new java.util.Date()
                )
        );

        mockMvc.perform(get("/getLocation").param("userName", "jon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location.latitude").value(10.0))
                .andExpect(jsonPath("$.location.longitude").value(20.0));
    }

    @Test
    void testGetNearbyAttractions() throws Exception {
        when(tourGuideService.getNearbyAttractionsDTO("jon"))
                .thenReturn(List.of(
                        new NearbyAttractionDTO(
                                "Attraction A",
                                1.0, 2.0,
                                3.0, 4.0,
                                10.5,
                                100
                        )
                ));

        mockMvc.perform(get("/getNearbyAttractions").param("userName", "jon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attractionName").value("Attraction A"))
                .andExpect(jsonPath("$[0].distance").value(10.5))
                .andExpect(jsonPath("$[0].rewardPoints").value(100));
    }

    @Test
    void testGetRewards() throws Exception {
        when(tourGuideService.getUser("jon")).thenReturn(user);
        when(tourGuideService.getUserRewards(user)).thenReturn(List.of());

        mockMvc.perform(get("/getRewards").param("userName", "jon"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetTripDeals() throws Exception {
        when(tourGuideService.getUser("jon")).thenReturn(user);
        when(tourGuideService.getTripDeals(user)).thenReturn(List.of());

        mockMvc.perform(get("/getTripDeals").param("userName", "jon"))
                .andExpect(status().isOk());
    }
}
