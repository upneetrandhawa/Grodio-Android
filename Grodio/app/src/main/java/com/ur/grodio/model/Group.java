package com.ur.grodio.model;

import android.util.Log;

import java.util.Date;

public class Group {

    private final String TAG = this.getClass().getSimpleName();

    private String groupName;
    private String pin;
    private String songURL;
    private String songName;
    private int songStartTime;
    private boolean isPlaying;
    private boolean masterSync;
    private Date lastTimeSongPlaybackWasStarted;
    private Date creationDate;

    public Group(String groupName, String pin, String songURL, String songName, int songStartTime, boolean isPlaying, boolean masterSync, Date lastTimeSongPlaybackWasStarted, Date creationDate) {
        this.groupName = groupName;
        this.pin = pin;
        this.songURL = songURL;
        this.songName = groupName;
        this.songStartTime = songStartTime;
        this.isPlaying = isPlaying;
        this.masterSync = masterSync;
        this.lastTimeSongPlaybackWasStarted = lastTimeSongPlaybackWasStarted;
        this.creationDate = creationDate;
    }

    public Group(){
        Log.d(TAG, "Group()");

    }

    @Override
    public String toString() {
        return "Group{" +
                "TAG='" + TAG + '\'' +
                ", groupName='" + groupName + '\'' +
                ", pin='" + pin + '\'' +
                ", songURL='" + songURL + '\'' +
                ", songName='" + songName + '\'' +
                ", songStartTime=" + songStartTime +
                ", isPlaying=" + isPlaying +
                ", masterSync=" + masterSync +
                ", lastTimeSongPlaybackWasStarted=" + lastTimeSongPlaybackWasStarted +
                ", creationDate=" + creationDate +
                '}';
    }

    public String getGroupName() {
        return groupName;
    }

    public String getPin() {
        return pin;
    }

    public String getSongURL() {
        return songURL;
    }

    public void setSongURL(String songURL) {
        this.songURL = songURL;
    }

    public String getSongName() {
        return songName;
    }

    public void setSongName(String songName) {
        this.songName = songName;
    }

    public int getSongStartTime() {
        return songStartTime;
    }

    public void setSongStartTime(int songStartTime) {
        this.songStartTime = songStartTime;
    }

    public boolean isIsPlaying() {
        return isPlaying;
    }

    public void setIsPlaying(boolean isPlaying) {
        this.isPlaying = isPlaying;
    }

    public boolean isMasterSync() {
        return masterSync;
    }

    public void setMasterSync(boolean masterSync) {
        this.masterSync = masterSync;
    }

    public Date getLastTimeSongPlaybackWasStarted() {
        return lastTimeSongPlaybackWasStarted;
    }

    public void setLastTimeSongPlaybackWasStarted(Date lastTimeSongPlaybackWasStarted) {
        this.lastTimeSongPlaybackWasStarted = lastTimeSongPlaybackWasStarted;
    }

    public Date getCreationDate() {
        return creationDate;
    }


}
