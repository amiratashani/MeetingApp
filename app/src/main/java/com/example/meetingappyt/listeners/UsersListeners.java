package com.example.meetingappyt.listeners;

import com.example.meetingappyt.models.User;

public interface UsersListeners {

    void initiateVideoMeeting(User user);

    void initiateAudioMeeting(User user);

    void onMultipleUserAction(Boolean isMultipleUsersSelected);

}
