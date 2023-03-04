package com.ur.grodio;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.SeekBar;
import android.provider.Settings;
import android.provider.Settings.System;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;

import com.google.firebase.storage.UploadTask;
import com.ur.grodio.databinding.ActivityPlayerBinding;
import com.ur.grodio.model.Group;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class PlayerActivity extends AppCompatActivity {

    private final String TAG = this.getClass().getSimpleName();
    private final int SEEK_DURATION = 15;
    private final int CHOOSE_AUDIO_INTENT = 1;

    private ActivityPlayerBinding binding;

    private Group group;
    private Uri chosenFileUri;

    private Boolean isMasterOutputEnabled = false;

    private Runnable updateSongSeekbarRunnable = null;
    private ScheduledExecutorService songSeekBarExecutor = null;
    private ScheduledFuture songSeekBarFuture = null;

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager = null;

    private FirebaseFirestore firebaseDB;
    private CollectionReference firebaseDBGroupsRef;
    private FirebaseStorage firebaseStorage;
    private StorageReference firebaseStorageSongsRef;
    private ListenerRegistration firestoreGroupDocumentListener;


    private ContentObserver mVolumeObserver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        binding = ActivityPlayerBinding.inflate(getLayoutInflater());

        mediaPlayer = new MediaPlayer();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        initFirebase();

        Bundle extras = getIntent().getExtras();

        if (extras!= null) {
            Bundle bundle = extras.getBundle("bundle");
            if (bundle != null){
                boolean creatingGroup = bundle.getBoolean("isCreatingGroup");
                String groupName = bundle.getString("validGroupName");
                String groupPin = bundle.getString("validGroupPin");
                Log.d(TAG, "onCreate(): isCreatingGroup = " + creatingGroup);
                Log.d(TAG, "onCreate(): validGroupName = " + groupName);
                Log.d(TAG, "onCreate(): groupPin = " + groupPin);

                if (creatingGroup){
                    createFirebaseDocumentFromGroupName(groupName, groupPin);
                }
                else{
                    getFirebaseDocumentFromGroupName(groupName);
                }
            }
            else {
                Log.d(TAG, "onCreate(): bundle null");
            }
        }
        else {
            Log.d(TAG, "onCreate(): extras null");
        }

        binding.playPauseimageButton.setEnabled(false);
        binding.playPauseimageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onCreate(): playPauseimageButton: OnClickListener()");
                playPauseButtonPressed();
            }
        });

        binding.seekBackwardImageButton.setEnabled(false);
        binding.seekBackwardImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onCreate(): seekBackwardImageButton: OnClickListener()");
                seekBackwardButtonPressed();
            }
        });

        binding.seekForwardImageButton.setEnabled(false);
        binding.seekForwardImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onCreate(): seekForwardImageButton: OnClickListener()");
                seekForwardButtonPressed();
            }
        });

        binding.masterOutputImageButton.setEnabled(false);
        binding.masterOutputImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onCreate(): masterOutputImageButton: OnClickListener()");
                masterOutputButtonPressed();
            }
        });

        binding.masterSyncImageButton.setEnabled(false);
        binding.masterSyncImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onCreate(): masterSyncImageButton: OnClickListener()");
                masterSyncButtonPressed();
            }
        });

        binding.chooseSongButton.setEnabled(false);
        binding.chooseSongButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onCreate(): chooseSongButton: OnClickListener()");
                chooseSongButtonPressed();
            }
        });

        binding.broadcastSongButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onCreate(): broadcastSongButton: OnClickListener()");
                broadcastSongButtonPressed();
            }
        });

        binding.volumeSeekBar.setEnabled(false);
        binding.volumeSeekBar.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        binding.volumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));

        mVolumeObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                Log.d(TAG, "onCreate(): mVolumeObserver: onChange(): volume = " + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                super.onChange(selfChange);
                if (binding.volumeSeekBar != null && audioManager != null) {
                    binding.volumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                }
            }
        };

        getApplicationContext().getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, mVolumeObserver);


        binding.volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Log.d(TAG, "onCreate(): volumeSeekBar: onProgressChanged(): i = " + i);

                if (b) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,i,AudioManager.FLAG_PLAY_SOUND);

                float volume = (float) i/audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                Log.d(TAG, "onCreate(): volumeSeekBar: onProgressChanged(): float volume = " + volume);

                mediaPlayer.setVolume(volume, volume);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "onCreate(): volumeSeekBar: onStartTrackingTouch()");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "onCreate(): volumeSeekBar: onStopTrackingTouch()");
            }
        });

        binding.songSeekBar.setEnabled(false);
        binding.songSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Log.d(TAG, "onCreate(): songSeekBar: onProgressChanged()");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "onCreate(): songSeekBar: onStartTrackingTouch()");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "onCreate(): songSeekBar: onStopTrackingTouch()");
                songSeekbarValueChanged(seekBar.getProgress());
            }
        });

        setContentView(binding.getRoot());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaPlayer.release();
        mediaPlayer = null;
        songSeekBarFuture.cancel(true);
        updateSongSeekbarRunnable = null;
        songSeekBarExecutor.shutdown();
        songSeekBarExecutor = null;

        //TODO
    }

    private void initFirebase(){
        Log.d(TAG, "initFirebase()");
        firebaseDB = FirebaseFirestore.getInstance();
        firebaseDBGroupsRef = firebaseDB.collection("groups");
        firebaseStorage = FirebaseStorage.getInstance();
        firebaseStorageSongsRef = firebaseStorage.getReference("songs");

    }

    private void createFirebaseDocumentFromGroupName(String groupName, String groupPin) {
        Log.d(TAG, "createFirebaseDocumentFromGroupName(): groupName = " + groupName + " groupPin = " + groupPin);

        Group newGroup = new Group(groupName,
                groupPin,
                "",
                "",
                0,
                false,
                false,
                Calendar.getInstance().getTime(),
                Calendar.getInstance().getTime());
        Log.d(TAG, "createFirebaseDocumentFromGroupName(): date = " + Calendar.getInstance().getTime());

        firebaseDBGroupsRef.document(groupName).set(newGroup).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.d(TAG, "createFirebaseDocumentFromGroupName(): onComplete()");

                if (task.isSuccessful()){
                    Log.d(TAG, "createFirebaseDocumentFromGroupName(): onComplete(): create document " + groupName + " successful");

                    group = newGroup;

                    //update view
                    binding.groupNameTextView.setText(groupName);
                    addToStatusUpdatesTextView("tuned into " + groupName);
                    addToStatusUpdatesTextView("choose a song to play");
                    attachListenerToFirestoreDocumentAndGetUpdates();

                }
                else {
                    Log.d(TAG, "createFirebaseDocumentFromGroupName(): onComplete(): error writing group to Firestore");
                }
            }
        });


    }

    private void getFirebaseDocumentFromGroupName(String groupName) {
        Log.d(TAG, "getFirebaseDocumentFromGroupName(): groupName = " + groupName);

        firebaseDBGroupsRef.document(groupName).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                Log.d(TAG, "getFirebaseDocumentFromGroupName(): onComplete()");

                if (task.isSuccessful()){
                    Log.d(TAG, "getFirebaseDocumentFromGroupName(): onComplete(): success");

                    if (task.getResult() == null){
                        Log.d(TAG, "getFirebaseDocumentFromGroupName(): onComplete(): data is null");
                        return;
                    }

                    Log.d(TAG, "getFirebaseDocumentFromGroupName(): onComplete(): data = " + task.getResult().getData().toString());
                    try {
                        updateViewWithFirebaseData(task.getResult());
                        attachListenerToFirestoreDocumentAndGetUpdates();
                        addToStatusUpdatesTextView("tuned into " + groupName);
                    } catch (IOException e) {
                        Log.d(TAG, "getFirebaseDocumentFromGroupName(): onComplete(): updateViewWithFirebaseData() exception");
                        e.printStackTrace();
                    }

                }
                else {
                    Log.d(TAG, "getFirebaseDocumentFromGroupName(): onComplete(): error fetching data");
                }
            }
        });
    }

    private void updateViewWithFirebaseData(DocumentSnapshot snapshot) throws IOException {
        Log.d(TAG, "updateViewWithFirebaseData(): data = " + snapshot.toString());

        group = snapshot.toObject(Group.class);


        Log.d(TAG, "getFirebaseDocumentFromGroupName(): group = " + group.toString());

        //update group label
        binding.groupNameTextView.setText(group.getGroupName());


        //init audioPlayer
        try {
            initAudioPlayer(group.getSongURL());
        } catch (IOException e) {
            Log.d(TAG, "getFirebaseDocumentFromGroupName(): initAudioPlayer() exception");
            e.printStackTrace();
        }
    }

    private void attachListenerToFirestoreDocumentAndGetUpdates(){
        Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates()");

        firestoreGroupDocumentListener = firebaseDBGroupsRef.document(group.getGroupName()).addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent()");

                if (error != null) {
                    Log.w(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): Listen failed.", error);
                    return;
                }

                if (value == null || !value.exists()) {
                    Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): data: null");
                    return;
                }

                String source = value != null && value.getMetadata().hasPendingWrites() ? "Local" : "Server";

                if (source.equals("Local")){
                    Log.w(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): source is Local returning");
                    return;
                }

                if (value.getData() == null){
                    Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): " + source + " data = null ");
                    return;
                }

                Map<String,Object> data = value.getData();
                Group updatedGroup = value.toObject(Group.class);
                Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): " + source + " data: " + data.toString());
                Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): updatedGroup: " + updatedGroup.toString());
                Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): oldGroup: " + group.toString());



                //compare for data changes

                //check if songURL is changed
                if(updatedGroup.getSongURL() == null){
                    Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): error retrieving data from server : songURL");
                    addToStatusUpdatesTextView("error getting data, relaunch app");
                    return;
                }
                if (!updatedGroup.getSongURL().equals(group.getSongURL())){
                    Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): songURL changed");
                    addToStatusUpdatesTextView("broadcasting song changed");
                    try {
                        updateViewWithFirebaseData(value);
                    } catch (IOException e) {
                        Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): updateViewWithFirebaseData() exception");
                        e.printStackTrace();
                    }
                    return;
                }

                //check if masterSync is changed
                if (updatedGroup.isMasterSync() != group.isMasterSync()){
                    Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): masterSync changed");
                    masterSyncChanged(updatedGroup.isMasterSync());
                }

                //check if isPlaying is changed
                boolean isPlayingChanged = true;
                if (updatedGroup.isIsPlaying() != group.isIsPlaying()){
                    Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): isPlaying changed");
                    
                    if (updatedGroup.isIsPlaying()){
                        addToStatusUpdatesTextView("broadcast was resumed");
                    }
                    else {
                        addToStatusUpdatesTextView("broadcast was paused");
                    }
                    playPauseStateChanged(updatedGroup.isIsPlaying());
                    group.setIsPlaying(updatedGroup.isIsPlaying());
                }
                else {
                    isPlayingChanged = false;
                }

                //check if lastTimeSongPlaybackWasStarted is changed
                if(updatedGroup.getLastTimeSongPlaybackWasStarted() == null){
                    Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): error retrieving data from server : lastTimeSongPlaybackWasStarted");
                    addToStatusUpdatesTextView("error getting data, relaunch app");
                    return;
                }
                if (!updatedGroup.getLastTimeSongPlaybackWasStarted().equals(group.getLastTimeSongPlaybackWasStarted())){
                    Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): lastTimeSongPlaybackWasStarted changed");

                    //check if songStartTime is changed
                    seekSongToAValue(updatedGroup.getSongStartTime(), updatedGroup.getLastTimeSongPlaybackWasStarted());

                    if (!isPlayingChanged && !updatedGroup.getLastTimeSongPlaybackWasStarted().equals(group.getCreationDate())){
                        if (updatedGroup.getSongStartTime() < group.getSongStartTime()){
                            Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): broadcast was sought backward");
                            addToStatusUpdatesTextView("broadcast was sought backward");
                        }
                        else {
                            Log.d(TAG, "attachListenerToFirestoreDocumentAndGetUpdates(): onEvent(): broadcast was sought forward");
                            addToStatusUpdatesTextView("broadcast was sought forward");
                        }
                    }

                    group.setSongStartTime(updatedGroup.getSongStartTime());
                    group.setLastTimeSongPlaybackWasStarted(updatedGroup.getLastTimeSongPlaybackWasStarted());
                }

            }
        });
    }

    private void chooseSongButtonPressed(){
        Log.d(TAG, "chooseSongButtonPressed()");

        Intent chooseSongIntent = new Intent();
        chooseSongIntent.setType("audio/*");
        chooseSongIntent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(chooseSongIntent,CHOOSE_AUDIO_INTENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        Log.d(TAG, "onActivityResult(): requestCode = " + requestCode);

        if(requestCode == CHOOSE_AUDIO_INTENT){

            if(resultCode == RESULT_OK){

                //the selected audio.
                Uri uri = data.getData();
                Log.d(TAG, "onActivityResult(): uri = " + uri.toString());

                String fileName = getFileNameFromUri(uri);

                binding.chosenSongNameTextView.setText(fileName);

                chosenFileUri = uri;
                binding.broadcastSongButton.setVisibility(View.VISIBLE);

                addToStatusUpdatesTextView("you chose " + fileName);

                //TODO: dispatch later 2 sec
                addToStatusUpdatesTextView("press Broadcast song to broadcast");


            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initAudioPlayer(String url) throws IOException {
        Log.d(TAG, "initAudioPlayer(): url = " + url);

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        mediaPlayer.setDataSource(url);
        mediaPlayer.prepare();

        addToStatusUpdatesTextView("beginning stream");

        setupAudioPlaybackControlsAndMetadata();

        if (group.isIsPlaying()) {
            addToStatusUpdatesTextView("song being broadcasted");
        }
        else {
            addToStatusUpdatesTextView("song selected but broadcasting paused");
        }
        playPauseStateChanged(group.isIsPlaying());

        seekSongToAValue(group.getSongStartTime(), group.getLastTimeSongPlaybackWasStarted());

        startSongSeekBarUpdate();

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                Log.d(TAG, "initAudioPlayer(): mediaPlayer onPrepared()");
                setupAudioPlaybackControlsAndMetadata();

                if (group.isIsPlaying()) {
                    addToStatusUpdatesTextView("song being broadcasted");
                }
                else {
                    addToStatusUpdatesTextView("song selected but broadcasting paused");
                }
                playPauseStateChanged(group.isIsPlaying());

                seekSongToAValue(group.getSongStartTime(), group.getLastTimeSongPlaybackWasStarted());

                startSongSeekBarUpdate();

                //TODO
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                Log.d(TAG, "initAudioPlayer(): mediaPlayer onCompletion()");
                playerFinishedPlayingSong();
                mediaPlayer.setOnCompletionListener(null);
            }
        });


    }

    private void setupAudioPlaybackControlsAndMetadata(){
        Log.d(TAG, "setupAudioPlaybackControlsAndMetadata()");

        binding.songPlayingNameTextView.setText(group.getSongName());

        //enable sliders
        binding.songSeekBar.setEnabled(true);
        binding.volumeSeekBar.setEnabled(true);

        //enable buttons
        binding.seekBackwardImageButton.setEnabled(true);
        binding.seekForwardImageButton.setEnabled(true);
        binding.playPauseimageButton.setEnabled(true);
        binding.masterOutputImageButton.setEnabled(true);
        binding.masterSyncImageButton.setEnabled(true);
        binding.chooseSongButton.setEnabled(true);

        //TODO updatePLayerVolumeAndVolumeSlider();

        int songDurationInSeconds = mediaPlayer.getDuration()/1000;
        int currentPlaybackTime = mediaPlayer.getCurrentPosition()/1000;

        Log.d(TAG, "setupAudioPlaybackControlsAndMetadata(): songDurationInSeconds = " + songDurationInSeconds);
        Log.d(TAG, "setupAudioPlaybackControlsAndMetadata(): currentPlaybackTime = " + currentPlaybackTime);

        binding.songPlaybackCurrentTimeTextView.setText(getTimeIntervalFromSeconds(currentPlaybackTime));
        binding.songPlaybackTotalTimeTextView.setText(getTimeIntervalFromSeconds(songDurationInSeconds));

        binding.songSeekBar.setProgress(0);
        binding.songSeekBar.setMax(songDurationInSeconds);

        binding.playPauseimageButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
    }

    private void startSongSeekBarUpdate(){
        Log.d(TAG, "startSongSeekBarUpdate()");

        if (songSeekBarExecutor == null){
            songSeekBarExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        if (updateSongSeekbarRunnable == null){
            updateSongSeekbarRunnable = new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateSongSeekbar();
                        }
                    });
                }
            };
        }

        ScheduledFuture songSeekBarFuture = songSeekBarExecutor.scheduleAtFixedRate(
                updateSongSeekbarRunnable,
                0,
                1000,
                TimeUnit.MILLISECONDS);


    }

    private void updateSongSeekbar(){
        Log.d(TAG, "updateSongSeekbar()");

        if (mediaPlayer == null) Log.d(TAG, "updateSongSeekbar(): mediaPlayer null");
        Log.d(TAG, "updateSongSeekbar(): mediaPlayer playing = " + mediaPlayer.isPlaying());

        if (mediaPlayer != null && mediaPlayer.isPlaying()){

            int songDurationInSeconds = mediaPlayer.getDuration()/1000;
            int currentPlaybackTime = mediaPlayer.getCurrentPosition()/1000;
            int songDurationLeft = songDurationInSeconds - currentPlaybackTime;

            Log.d(TAG, "updateSongSeekbar(): currentPlaybackTime = " + currentPlaybackTime + ", songDurationLeft = " + songDurationLeft);

            if (!binding.songSeekBar.isPressed()){
                Log.d(TAG, "updateSongSeekbar(): songSeekBar not pressed");
                binding.songSeekBar.setProgress(currentPlaybackTime);
            }

            binding.songPlaybackCurrentTimeTextView.setText(getTimeIntervalFromSeconds(currentPlaybackTime));
            binding.songPlaybackTotalTimeTextView.setText(getTimeIntervalFromSeconds(songDurationLeft));

        }
    }

    private void playPauseButtonPressed(){
        Log.d(TAG, "playPauseButtonPressed(): mediaPlayer playing = " + mediaPlayer.isPlaying());

        Map<String,Object> updatedData = new HashMap<>();
        updatedData.put("isPlaying", !group.isIsPlaying());
        updatedData.put("songStartTime", mediaPlayer.getCurrentPosition()/1000);
        updatedData.put("lastTimeSongPlaybackWasStarted", FieldValue.serverTimestamp());

        firebaseDBGroupsRef.document(group.getGroupName()).update(updatedData).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.d(TAG, "playPauseButtonPressed(): onComplete(): task isSuccessful = " + task.isSuccessful());

                if (!task.isSuccessful()){
                    Log.d(TAG, "playPauseButtonPressed(): onComplete(): Error updating data = " + task.toString());
                }
                else {
                    Log.d(TAG, "playPauseButtonPressed(): onComplete(): Document written successfully = " + task.toString());
                }
            }
        });
    }

    private void playPauseStateChanged(boolean playerTobePlayed) {
        Log.d(TAG, "playPauseStateChanged(): playerTobePlayed = " + playerTobePlayed);

        if (mediaPlayer == null){
            Log.d(TAG, "playPauseStateChanged(): media player = null");
            addToStatusUpdatesTextView("media player error");
            return;
        }

        if (playerTobePlayed){
            mediaPlayer.start();
            binding.playPauseimageButton.setImageResource(R.drawable.ic_baseline_pause_24);
        }
        else {
            mediaPlayer.pause();
            binding.playPauseimageButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
            //if (songSeekBarFuture != null) songSeekBarFuture.cancel(true);
        }
    }

    private void playerFinishedPlayingSong(){
        Log.d(TAG, "playerFinishedPlayingSong(): mediaPlayer playing = " + mediaPlayer.isPlaying());

        Map<String,Object> updatedData = new HashMap<>();
        updatedData.put("isPlaying", false);
        updatedData.put("songStartTime", 0);
        updatedData.put("lastTimeSongPlaybackWasStarted", FieldValue.serverTimestamp());

        firebaseDBGroupsRef.document(group.getGroupName()).update(updatedData).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.d(TAG, "playerFinishedPlayingSong(): onComplete(): task isSuccessful = " + task.isSuccessful());

                if (!task.isSuccessful()){
                    Log.d(TAG, "playerFinishedPlayingSong(): onComplete(): Error updating data = " + task.toString());
                }
                else {
                    Log.d(TAG, "playerFinishedPlayingSong(): onComplete(): Document written successfully = " + task.toString());
                    addToStatusUpdatesTextView("broadcast finished");
                    addToStatusUpdatesTextView("press play or choose another song");
                }
            }
        });
    }

    private void seekBackwardButtonPressed(){
        Log.d(TAG, "seekBackwardButtonPressed(): mediaPlayer playing = " + mediaPlayer.isPlaying());

        int songCurrentPlaybackTime = mediaPlayer.getCurrentPosition()/1000;

        int newPlaybackTime =  songCurrentPlaybackTime - SEEK_DURATION < 0 ? 0 : songCurrentPlaybackTime - SEEK_DURATION;

        Map<String,Object> updatedData = new HashMap<>();
        updatedData.put("songStartTime", newPlaybackTime);
        updatedData.put("lastTimeSongPlaybackWasStarted", FieldValue.serverTimestamp());

        firebaseDBGroupsRef.document(group.getGroupName()).update(updatedData).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.d(TAG, "seekBackwardButtonPressed(): onComplete(): task isSuccessful = " + task.isSuccessful());

                if (!task.isSuccessful()){
                    Log.d(TAG, "seekBackwardButtonPressed(): onComplete(): Error updating data = " + task.toString());
                }
                else {
                    Log.d(TAG, "seekBackwardButtonPressed(): onComplete(): Document written successfully = " + task.toString());
                }
            }
        });
    }

    private void seekForwardButtonPressed(){
        Log.d(TAG, "seekForwardButtonPressed(): mediaPlayer playing = " + mediaPlayer.isPlaying());

        int songCurrentPlaybackTime = mediaPlayer.getCurrentPosition()/1000;
        int songDurationInSeconds = mediaPlayer.getDuration()/1000;

        int newPlaybackTime =  songCurrentPlaybackTime + SEEK_DURATION > songDurationInSeconds ? songDurationInSeconds : songCurrentPlaybackTime + SEEK_DURATION;

        Map<String,Object> updatedData = new HashMap<>();
        updatedData.put("songStartTime", newPlaybackTime);
        updatedData.put("lastTimeSongPlaybackWasStarted", FieldValue.serverTimestamp());

        firebaseDBGroupsRef.document(group.getGroupName()).update(updatedData).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.d(TAG, "seekForwardButtonPressed(): onComplete(): task isSuccessful = " + task.isSuccessful());

                if (!task.isSuccessful()){
                    Log.d(TAG, "seekForwardButtonPressed(): onComplete(): Error updating data = " + task.toString());
                }
                else {
                    Log.d(TAG, "seekForwardButtonPressed(): onComplete(): Document written successfully = " + task.toString());
                }
            }
        });

    }

    private void songSeekbarValueChanged(int newStartTime){
        Log.d(TAG, "songSeekbarValueChanged(): newStartTime = " + newStartTime);

        Map<String,Object> updatedData = new HashMap<>();
        updatedData.put("songStartTime", newStartTime);
        updatedData.put("lastTimeSongPlaybackWasStarted", FieldValue.serverTimestamp());

        firebaseDBGroupsRef.document(group.getGroupName()).update(updatedData).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.d(TAG, "songSeekbarValueChanged(): onComplete(): task isSuccessful = " + task.isSuccessful());

                if (!task.isSuccessful()){
                    Log.d(TAG, "songSeekbarValueChanged(): onComplete(): Error updating data = " + task.toString());
                }
                else {
                    Log.d(TAG, "songSeekbarValueChanged(): onComplete(): Document written successfully = " + task.toString());
                }
            }
        });

    }

    private void seekSongToAValue(int songStartTime, Date timeSeekWasRequested) {
        Log.d(TAG, "seekSongToAValue(): songStartTime = " + songStartTime + ", timeSeekWasRequested = " + timeSeekWasRequested.toString());

        Date currentDate = new Date();
        int secondsDiff = (int)((currentDate.getTime() - timeSeekWasRequested.getTime())/1000);
        Log.d(TAG, "seekSongToAValue(): secondsDiff = " + secondsDiff);

        int seekValue = secondsDiff + songStartTime;

        Log.d(TAG, "seekSongToAValue(): seekValue = " + seekValue + ", duration = " + mediaPlayer.getDuration()/1000);

        if (seekValue > mediaPlayer.getDuration()/1000){
            seekValue = 0;
        }
        Log.d(TAG, "seekSongToAValue(): seekValue = " + seekValue);

        mediaPlayer.seekTo(seekValue*1000);

    }
    private void masterSyncButtonPressed() {
        Log.d(TAG, "masterSyncButtonPressed()");

        if (!mediaPlayer.isPlaying()){
            addToStatusUpdatesTextView("master sync finished");
            return;
        }

        Log.d(TAG, "masterSyncButtonPressed(): setting masterSync true");

        Map<String,Object> updatedData = new HashMap<>();
        updatedData.put("masterSync", true);
        updatedData.put("isPlaying", !group.isIsPlaying());
        updatedData.put("songStartTime", mediaPlayer.getCurrentPosition()/1000);
        updatedData.put("lastTimeSongPlaybackWasStarted", FieldValue.serverTimestamp());

        firebaseDBGroupsRef.document(group.getGroupName()).update(updatedData).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Log.d(TAG, "masterSyncButtonPressed(): onComplete(): task isSuccessful = " + task.isSuccessful());

                if (!task.isSuccessful()){
                    Log.d(TAG, "masterSyncButtonPressed(): onComplete(): Error updating data = " + task.toString());
                }
                else {
                    Log.d(TAG, "masterSyncButtonPressed(): onComplete(): Document written successfully: masterSync set true = " + task.toString());

                    Timer timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Log.d(TAG, "masterSyncButtonPressed(): run()");
                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    Map<String,Object> updatedData2 = new HashMap<>();
                                    updatedData2.put("masterSync", false);
                                    updatedData2.put("isPlaying", !group.isIsPlaying());
                                    updatedData2.put("songStartTime", mediaPlayer.getCurrentPosition()/1000);
                                    updatedData2.put("lastTimeSongPlaybackWasStarted", FieldValue.serverTimestamp());

                                    firebaseDBGroupsRef.document(group.getGroupName()).update(updatedData2).addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            Log.d(TAG, "masterSyncButtonPressed(): run(): onComplete(): task isSuccessful = " + task.isSuccessful());

                                            if (!task.isSuccessful()){
                                                Log.d(TAG, "masterSyncButtonPressed(): run(): onComplete(): Error updating data = " + task.toString());
                                            }
                                            else {
                                                Log.d(TAG, "masterSyncButtonPressed(): run(): onComplete(): Document written successfully masterSync set false = " + task.toString());
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    },3000);
                }
            }
        });
    }

    private void masterSyncChanged(boolean masterSync) {
        Log.d(TAG, "masterSyncChanged(): masterSync = " + masterSync);

        if (masterSync){
            //TODO master sync rotate start
            addToStatusUpdatesTextView("master sync initiated");
            group.setMasterSync(true);
            isMasterOutputEnabled = true;
            masterOutputButtonPressed();
        }
        else {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "masterSyncChanged(): run()");
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            isMasterOutputEnabled = false;
                            masterOutputButtonPressed();
                            group.setMasterSync(false);
                            addToStatusUpdatesTextView("master sync finished");
                            //TODO master sync rotate stop
                        }
                    });
                }
            },200);
        }
    }

    private void masterOutputButtonPressed() {
        Log.d(TAG, "masterOutputButtonPressed(): isMasterOutputEnabled = " + isMasterOutputEnabled);

        isMasterOutputEnabled = !isMasterOutputEnabled;

        Boolean value = isMasterOutputEnabled;
        Log.d(TAG, "masterOutputButtonPressed(): new isMasterOutputEnabled = " + isMasterOutputEnabled);

        Float volume = value ? (float) (binding.volumeSeekBar.getProgress()/audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) : 0f;

        mediaPlayer.setVolume(volume,volume);

        binding.songSeekBar.setEnabled(value);
        binding.volumeSeekBar.setEnabled(value);
        binding.seekBackwardImageButton.setEnabled(value);
        binding.seekForwardImageButton.setEnabled(value);
        binding.playPauseimageButton.setEnabled(value);
        binding.masterSyncImageButton.setEnabled(value);
        binding.broadcastSongButton.setEnabled(value);
        binding.chooseSongButton.setEnabled(value);

        binding.songSeekBar.setAlpha((value ? 1.0f : 0.5f));
        binding.volumeSeekBar.setAlpha((value ? 1.0f : 0.5f));
        binding.seekBackwardImageButton.setAlpha((value ? 1.0f : 0.5f));
        binding.seekForwardImageButton.setAlpha((value ? 1.0f : 0.5f));
        binding.playPauseimageButton.setAlpha((value ? 1.0f : 0.5f));
        binding.masterSyncImageButton.setAlpha((value ? 1.0f : 0.5f));
        binding.broadcastSongButton.setAlpha((value ? 1.0f : 0.5f));
        binding.chooseSongButton.setAlpha((value ? 1.0f : 0.5f));

        if (group.isMasterSync()){
            binding.masterSyncImageButton.setEnabled(value);
            binding.masterSyncImageButton.setAlpha((value ? 1.0f : 0.5f));
        }

        binding.masterOutputImageButton.setImageResource(value ? R.drawable.ic_baseline_power_settings_new_24 : R.drawable.ic_baseline_power_settings_new_24_red);

        addToStatusUpdatesTextView(value ? "master output enabled" : "master output disabled");
    }

    private void broadcastSongButtonPressed() {
        Log.d(TAG, "broadcastSongButtonPressed()");

        binding.broadcastSongButton.setVisibility(View.INVISIBLE);
        addToStatusUpdatesTextView("broadcasting song");
        uploadSongToFirebaseStorage();
    }

    private void uploadSongToFirebaseStorage(){
        Log.d(TAG, "uploadSongToFirebaseStorage()");

        addToStatusUpdatesTextView("uploading song...");

        final StorageReference songRef = firebaseStorageSongsRef.child(group.getGroupName()).child(getFileNameFromUri(chosenFileUri));

        UploadTask uploadTask = songRef.putFile(chosenFileUri);

        // Listen for state changes, errors, and completion of the upload.
        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                Log.d(TAG, "uploadSongToFirebaseStorage(): onProgress()");
                double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                Log.d(TAG, "Upload is " + progress + "% done");
                addToStatusUpdatesTextView("upload completed : " + progress);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.d(TAG, "uploadSongToFirebaseStorage(): onFailure() exception = "+exception.toString());
                // Handle unsuccessful uploads
                addToStatusUpdatesTextView("error uploading song");
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Log.d(TAG, "uploadSongToFirebaseStorage(): onSuccess()");
                // Handle successful uploads on complete
                // ...

                songRef.getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                    @Override
                    public void onComplete(@NonNull Task<Uri> task) {
                        Log.d(TAG, "uploadSongToFirebaseStorage(): onSuccess(): getDownloadUrl(): onComplete(): isSuccessful = " + task.isSuccessful());
                        if (task.isSuccessful()){
                            Uri downloadUri = task.getResult();
                            Log.d(TAG, "uploadSongToFirebaseStorage(): onSuccess(): getDownloadUrl(): onComplete(): downloadUri = " + downloadUri.toString());

                            deleteFileFromFirebaseStorage(group.getSongName());

                            Map<String,Object> updatedData = new HashMap<>();
                            updatedData.put("songURL", downloadUri.toString());
                            updatedData.put("songName", getFileNameFromUri(chosenFileUri));
                            updatedData.put("songStartTime", 0);
                            updatedData.put("lastTimeSongPlaybackWasStarted", FieldValue.serverTimestamp());

                            firebaseDBGroupsRef.document(group.getGroupName()).update(updatedData).addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    Log.d(TAG, "uploadSongToFirebaseStorage(): onSuccess(): getDownloadUrl(): onComplete(): update(): onComplete(): task isSuccessful = " + task.isSuccessful());

                                    if (!task.isSuccessful()){
                                        Log.d(TAG, "uploadSongToFirebaseStorage(): onSuccess(): getDownloadUrl(): onComplete(): update(): onComplete(): Error updating data = " + task.toString());
                                    }
                                    else {
                                        Log.d(TAG, "uploadSongToFirebaseStorage(): onSuccess(): getDownloadUrl(): onComplete(): update(): onComplete(): Document written successfully = " + task.toString());
                                    }
                                }
                            });
                        }
                        else {
                            addToStatusUpdatesTextView("error uploading song");
                        }
                    }
                });
            }
        });

    }

    private void deleteFileFromFirebaseStorage(String name){
        Log.d(TAG, "deleteFileFromFirebaseStorage(): name = " + name);

        firebaseStorageSongsRef.child(group.getGroupName()).child(group.getSongName()).delete().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                // File deleted successfully
                Log.d(TAG, "deleteFileFromFirebaseStorage(): onSuccess()");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Uh-oh, an error occurred!
                Log.d(TAG, "deleteFileFromFirebaseStorage(): onFailure(): exception = " + exception.toString());
            }
        });
    }

    private void addToStatusUpdatesTextView(String str){
        Log.d(TAG, "addToStatusUpdatesTextView(): str = " + str);

        if (group.isMasterSync()) {
            return;
        }

        String text = binding.statusUpdatesTextView.getText().toString();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "addToStatusUpdatesTextView(): run() str = " + str);
                if (text.equals("")){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.statusUpdatesTextView.setText(str);
                        }
                    });
                }
                else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.statusUpdatesTextView.setText(text + "\n" + str);
                        }
                    });
                }
            }
        },200);
    }

//    @Override
//    public boolean onKeyUp(int keyCode, KeyEvent event) {
//        Log.d(TAG, "onKeyUp(): event = " + event.toString());
//
//        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
//            binding.volumeSeekBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
//        }
//        return super.onKeyUp(keyCode, event);
//    }

    private String getFileNameFromUri(Uri uri) {
        Log.d(TAG, "getFileNameFromUri()");
        ContentResolver resolver = getContentResolver();
        Cursor returnCursor =
                resolver.query(uri, null, null, null, null);
        assert returnCursor != null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();
        return name;
    }

    private String getTimeIntervalFromSeconds(int seconds){
        Log.d(TAG, "getTimeIntervalFromSeconds(): seconds = " + seconds);

        int s = seconds % 60;
        int m = (seconds / 60) % 60;
        int h = (seconds / 3600);

        if (seconds > 3600) {
            return String.format("%02d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }
}
