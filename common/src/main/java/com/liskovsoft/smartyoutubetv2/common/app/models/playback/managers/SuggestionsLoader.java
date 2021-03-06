package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class SuggestionsLoader extends PlayerEventListenerHelper {
    private static final String TAG = SuggestionsLoader.class.getSimpleName();
    private final List<MetadataListener> mListeners = new ArrayList<>();
    private Disposable mMetadataAction;

    public interface MetadataListener {
        void onMetadata(MediaItemMetadata metadata);
    }

    @Override
    public void onSourceChanged(Video item) {
        loadSuggestions(item);
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public boolean onNextClicked() {
        disposeActions();
        return false;
    }

    @Override
    public boolean onPreviousClicked() {
        disposeActions();
        return false;
    }

    private void disposeActions() {
        if (mMetadataAction != null && !mMetadataAction.isDisposed()) {
            mMetadataAction.dispose();
        }
    }

    private void syncCurrentVideo(MediaItemMetadata mediaItemMetadata) {
        Video video = mController.getVideo();
        video.title = mediaItemMetadata.getTitle();
        video.description = mediaItemMetadata.getDescription();
        video.channelId = mediaItemMetadata.getChannelId();
        mController.setVideo(video);
    }

    private void loadSuggestions(Video video) {
        if (video == null) {
            Log.e(TAG, "loadSuggestions: video is null");
            return;
        }

        mController.clearSuggestions(); // clear previous videos

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mMetadataAction = mediaItemManager.getMetadataObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::loadSuggestions,
                           error -> Log.e(TAG, "loadSuggestions error: " + error));
    }

    private void loadSuggestions(MediaItemMetadata mediaItemMetadata) {
        syncCurrentVideo(mediaItemMetadata);

        callListener(mediaItemMetadata);

        List<MediaGroup> suggestions = mediaItemMetadata.getSuggestions();

        if (suggestions == null) {
            Log.e(TAG, "loadSuggestions: Can't obtain suggestions for video: " + mController.getVideo().title);
            return;
        }

        for (MediaGroup group : suggestions) {
            mController.updateSuggestions(VideoGroup.from(group));
        }
    }

    public void addMetadataListener(MetadataListener listener) {
        mListeners.add(listener);
    }

    private void callListener(MediaItemMetadata mediaItemMetadata) {
        if (mediaItemMetadata != null) {
            for (MetadataListener listener : mListeners) {
                listener.onMetadata(mediaItemMetadata);
            }
        }
    }
}
