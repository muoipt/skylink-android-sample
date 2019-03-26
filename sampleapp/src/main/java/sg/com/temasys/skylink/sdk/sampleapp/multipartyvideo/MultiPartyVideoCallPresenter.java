package sg.com.temasys.skylink.sdk.sampleapp.multipartyvideo;

import android.content.Context;
import android.util.Log;

import org.webrtc.SurfaceViewRenderer;

import java.util.concurrent.ConcurrentHashMap;

import sg.com.temasys.skylink.sdk.rtc.Info;
import sg.com.temasys.skylink.sdk.rtc.SkylinkCaptureFormat;
import sg.com.temasys.skylink.sdk.rtc.SkylinkConfig;
import sg.com.temasys.skylink.sdk.rtc.UserInfo;
import sg.com.temasys.skylink.sdk.sampleapp.BasePresenter;
import sg.com.temasys.skylink.sdk.sampleapp.service.MultiPartyVideoService;
import sg.com.temasys.skylink.sdk.sampleapp.service.model.PermRequesterInfo;
import sg.com.temasys.skylink.sdk.sampleapp.service.model.SkylinkPeer;
import sg.com.temasys.skylink.sdk.sampleapp.service.model.VideoLocalState;
import sg.com.temasys.skylink.sdk.sampleapp.setting.Config;
import sg.com.temasys.skylink.sdk.sampleapp.utils.AudioRouter;
import sg.com.temasys.skylink.sdk.sampleapp.utils.Constants;
import sg.com.temasys.skylink.sdk.sampleapp.utils.PermissionUtils;

import static sg.com.temasys.skylink.sdk.sampleapp.utils.Utils.toastLog;
import static sg.com.temasys.skylink.sdk.sampleapp.utils.Utils.toastLogLong;

/**
 * Created by muoi.pham on 20/07/18.
 * This class is responsible for processing multi videos call logic
 */
public class MultiPartyVideoCallPresenter extends BasePresenter implements MultiPartyVideoCallContract.Presenter {

    private final String TAG = MultiPartyVideoCallPresenter.class.getName();

    // view instance
    public MultiPartyVideoCallContract.View multiVideoCallView;

    // Service helps to work with SkylinkSDK
    private MultiPartyVideoService multiVideoCallService;

    //Permission helps to process media runtime permission
    private PermissionUtils permissionUtils;

    private Context context;

    private VideoLocalState videoLocalState = new VideoLocalState();

    // Map with PeerId as key for boolean state
    // that indicates if currently getting WebRTC stats for Peer.
    private ConcurrentHashMap<String, Boolean> isGettingWebrtcStats =
            new ConcurrentHashMap<String, Boolean>();

    public MultiPartyVideoCallPresenter(Context context) {
        this.context = context;
        this.multiVideoCallService = new MultiPartyVideoService(this.context);
        this.multiVideoCallService.setPresenter(this);
        this.permissionUtils = new PermissionUtils();
    }

    public void setView(MultiPartyVideoCallContract.View view) {
        multiVideoCallView = view;
        multiVideoCallView.setPresenter(this);
    }

    //----------------------------------------------------------------------------------------------
    // Override methods from BasePresenter for view to call
    // These methods are responsible for processing requests from view
    //----------------------------------------------------------------------------------------------

    @Override
    public void onViewRequestConnectedLayout() {
        Log.d(TAG, "[onViewRequestConnectedLayout]");

        //start to connect to room when entering room
        //if not being connected, then connect
        if (!multiVideoCallService.isConnectingOrConnected()) {

            //reset permission request states.
            permissionUtils.permQReset();

            //connect to room on Skylink connection
            processConnectToRoom();

            //after connected to skylink SDK, UI will be updated later on onServiceRequestConnect

            Log.d(TAG, "Try to connect when entering room");

        }
    }

    @Override
    public void onViewRequestExit() {
        //process disconnect from room
        multiVideoCallService.disconnectFromRoom();
        //after disconnected from skylink SDK, UI will be updated latter on onServiceRequestDisconnect
    }

    @Override
    public void onViewRequestResume() {
        // Toggle camera back to previous state if required.
        if (videoLocalState.isCameraMute()) {
            if (multiVideoCallService.getVideoView(null) != null) {
                multiVideoCallService.toggleCamera();
                videoLocalState.setCameraMute(false);
            }
        }
    }

    @Override
    public void onViewRequestPause() {
        // Stop camera while view paused
        if (multiVideoCallService.getVideoView(null) != null) {
            boolean toggleCamera = multiVideoCallService.toggleCamera(false);
            videoLocalState.setCameraMute(toggleCamera);
        }
    }

    @Override
    public void onViewRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // delegate to PermissionUtils to process the permissions
        permissionUtils.onRequestPermissionsResultHandler(requestCode, permissions, grantResults, TAG);
    }

    @Override
    public void onViewRequestSwitchCamera() {
        //change to back/front camera by SkylinkSDK
        multiVideoCallService.switchCamera();
    }

    @Override
    public boolean onViewRequestStartRecording() {
        // start recording the view call by SkylinkSDK (with SMR key)
        return multiVideoCallService.startRecording();
    }

    @Override
    public boolean onViewRequestStopRecording() {
        // stop recording the view call by SkylinkSDK (with SMR key)
        return multiVideoCallService.stopRecording();
    }

    @Override
    public void onViewRequestGetInputVideoResolution() {
        // get local video resolution by SkylinkSDK
        multiVideoCallService.getInputVideoResolution();
    }

    @Override
    public void onViewRequestGetSentVideoResolution(int peerIndex) {
        // get video resolution sent to remote peer(s)
        multiVideoCallService.getSentVideoResolution(peerIndex);
    }

    @Override
    public void onViewRequestGetReceivedVideoResolution(int peerIndex) {
        //get received video resolution from remote peer(s)
        multiVideoCallService.getReceivedVideoResolution(peerIndex);
    }

    @Override
    public void onViewRequestWebrtcStatsToggle(int peerIndex) {

        String peerId = multiVideoCallService.getPeerIdByIndex(peerIndex);

        if (peerId == null)
            return;

        Boolean gettingStats = isGettingWebrtcStats.get(peerId);
        if (gettingStats == null) {
            String log = "[SA][wStatsTog] Peer " + peerId +
                    " does not exist. Will not get WebRTC stats.";
            Log.e(TAG, log);
            return;
        }

        // Toggle the state of getting WebRTC stats to the opposite state.
        if (gettingStats) {
            gettingStats = false;
        } else {
            gettingStats = true;
        }
        isGettingWebrtcStats.put(peerId, gettingStats);
        processGetWStatsAll(peerId);
    }

    @Override
    public void onViewRequestGetTransferSpeeds(int peerIndex, int mediaDirection, int mediaType) {
        multiVideoCallService.getTransferSpeeds(peerIndex, mediaDirection, mediaType);
    }

    @Override
    public void onViewRequestRefreshConnection(int peerIndex, boolean iceRestart) {
        multiVideoCallService.refreshConnection(peerIndex, iceRestart);
    }

    @Override
    public String onViewRequestGetRoomIdAndNickname() {
        //get id and nickname of the room by SkylinkSDK
        return multiVideoCallService.getRoomIdAndNickname(Constants.CONFIG_TYPE.MULTI_PARTY_VIDEO);
    }

    @Override
    public int onViewRequestGetTotalInRoom() {
        //get total peers in room (include local peer)
        return multiVideoCallService.getTotalInRoom();
    }

    @Override
    public SurfaceViewRenderer onViewRequestGetVideoViewByIndex(int index) {
        return multiVideoCallService.getVideoViewByIndex(index);
    }

    /**
     * Get the specific peer object according to the index
     */
    @Override
    public SkylinkPeer onViewRequestGetPeerByIndex(int index) {
        return multiVideoCallService.getPeerByIndex(index);
    }

    @Override
    public Boolean onViewRequestGetWebRtcStatsState(int peerIndex) {
        SkylinkPeer peer = onViewRequestGetPeerByIndex(peerIndex);
        if (peer != null)
            return isGettingWebrtcStats.get(peer.getPeerId());
        return null;
    }

    //----------------------------------------------------------------------------------------------
    // Override methods from BasePresenter for service to call
    // These methods are responsible for processing requests from service
    //----------------------------------------------------------------------------------------------

    @Override
    public void onServiceRequestConnect(boolean isSuccessful) {
        if (isSuccessful) {
            //start audio routing if has audio config
            SkylinkConfig skylinkConfig = multiVideoCallService.getSkylinkConfig();
            if (skylinkConfig.hasAudioSend() && skylinkConfig.hasAudioReceive()) {
                AudioRouter.setPresenter(this);
                AudioRouter.startAudioRouting(context, Constants.CONFIG_TYPE.VIDEO);
            }

            multiVideoCallView.onPresenterRequestUpdateUIConnected(processGetRoomId());
        }
    }

    @Override
    public void onServiceRequestDisconnect() {
        //stop audio routing
        SkylinkConfig skylinkConfig = multiVideoCallService.getSkylinkConfig();
        if (skylinkConfig.hasAudioSend() && skylinkConfig.hasAudioReceive()) {
            AudioRouter.stopAudioRouting(context);
        }
    }

    @Override
    public void onServiceRequestLocalMediaCapture(SurfaceViewRenderer videoView) {
        String log = "[SA][onLocalMediaCapture] ";

        if (videoView == null) {
            log += "VideoView is null!";
            Log.d(TAG, log);

            SurfaceViewRenderer selfVideoView = multiVideoCallService.getVideoView(null);
            multiVideoCallView.onPresenterRequestAddSelfView(selfVideoView);

        } else {
            log += "Adding VideoView as selfView.";
            Log.d(TAG, log);
            multiVideoCallView.onPresenterRequestAddSelfView(videoView);
        }
    }

    @Override
    public void onServiceRequestInputVideoResolutionObtained(int width, int height, int fps, SkylinkCaptureFormat captureFormat) {
        String log = "[SA][VideoResInput] The current video input has width x height, fps: " +
                width + " x " + height + ", " + fps + " fps.\r\n";
        Log.d(TAG, log);
        toastLogLong(TAG, context, log);
    }

    @Override
    public void onServiceRequestReceivedVideoResolutionObtained(String peerId, int width, int height, int fps) {
        String log = "[SA][VideoResRecv] The current video received from Peer " + peerId +
                " has width x height, fps: " + width + " x " + height + ", " + fps + " fps.\r\n";
        Log.d(TAG, log);
        toastLogLong(TAG, context, log);
    }

    @Override
    public void onServiceRequestSentVideoResolutionObtained(String peerId, int width, int height, int fps) {
        String log = "[SA][VideoResSent] The current video sent to Peer " + peerId +
                " has width x height, fps: " + width + " x " + height + ", " + fps + " fps.\r\n";
        Log.d(TAG, log);
        toastLogLong(TAG, context, log);
    }

    @Override
    public void onServiceRequestRemotePeerJoin(SkylinkPeer skylinkPeer) {

        // add new peer button in action bar
        multiVideoCallView.onPresenterRequestChangeUiRemotePeerJoin(skylinkPeer, multiVideoCallService.getTotalPeersInRoom() - 1);

        // add new webRTCStats for peer
        isGettingWebrtcStats.put(skylinkPeer.getPeerId(), false);

        // add remote peer video view
        processAddRemoteView(skylinkPeer.getPeerId());
    }

    @Override
    public void onServiceRequestRemotePeerLeave(SkylinkPeer remotePeer, int removeIndex) {
        // do not process if the left peer is local peer
        if (removeIndex == -1)
            return;

        // Remove the peer in button in custom bar
        multiVideoCallView.onPresenterRequestChangeUIRemotePeerLeft(removeIndex, multiVideoCallService.getPeersList());

        // remove the   webRtStats of the peer
        isGettingWebrtcStats.remove(remotePeer.getPeerId());

        // remote the remote peer video view
        multiVideoCallView.onPresenterRequestRemoveRemotePeer(removeIndex);
    }

    @Override
    public void onServiceRequestRemotePeerConnectionRefreshed(String log, UserInfo remotePeerUserInfo) {
        log += "isAudioStereo:" + remotePeerUserInfo.isAudioStereo() + ".\r\n" +
                "video height:" + remotePeerUserInfo.getVideoHeight() + ".\r\n" +
                "video width:" + remotePeerUserInfo.getVideoHeight() + ".\r\n" +
                "video frameRate:" + remotePeerUserInfo.getVideoFps() + ".";
        toastLog(TAG, context, log);
    }

    @Override
    public void onServiceRequestRemotePeerAudioReceive(String log, UserInfo remotePeerUserInfo, String remotePeerId) {
        log += "isAudioStereo:" + remotePeerUserInfo.isAudioStereo() + ".\r\n";
        Log.d(TAG, log);
        toastLog(TAG, context, log);
    }

    @Override
    public void onServiceRequestRemotePeerVideoReceive(String log, UserInfo remotePeerUserInfo, String remotePeerId) {

        processAddRemoteView(remotePeerId);

        log += "video height:" + remotePeerUserInfo.getVideoHeight() + ".\r\n" +
                "video width:" + remotePeerUserInfo.getVideoHeight() + ".\r\n" +
                "video frameRate:" + remotePeerUserInfo.getVideoFps() + ".";
        Log.d(TAG, log);
        toastLog(TAG, context, log);
    }

    @Override
    public void onServiceRequestRecordingVideoLink(String recordingId, String peerId, String videoLink) {
        String peer = " Mixin";
        if (peerId != null) {
            peer = " Peer " + multiVideoCallService.getPeerIdNick(peerId) + "'s";
        }
        String msg = "Recording:" + recordingId + peer + " video link:\n" + videoLink;

        multiVideoCallView.onPresenterRequestDisplayVideoLinkInfo(recordingId, msg);

    }

    @Override
    public void onServiceRequestPermissionRequired(PermRequesterInfo info) {
        permissionUtils.onPermissionRequiredHandler(info, TAG, context, multiVideoCallView.onPresenterRequestGetFragmentInstance());
    }

    //----------------------------------------------------------------------------------------------
    // private methods for internal process
    //----------------------------------------------------------------------------------------------

    /**
     * Process connect to room on service layer and update UI accordingly
     */
    private void processConnectToRoom() {

        //connect to SkylinkSDK
        multiVideoCallService.connectToRoom(Constants.CONFIG_TYPE.MULTI_PARTY_VIDEO);

        //get roomName from setting
        String log = "Entering multi party videos room : \"" + Config.ROOM_NAME_PARTY + "\".";
        toastLog(TAG, context, log);
    }

    /**
     * Trigger processGetWebrtcStats for specific Peer in a loop if current state allows.
     * To stop loop, set {@link #isGettingWebrtcStats} to false.
     *
     * @param peerId
     */
    private void processGetWStatsAll(final String peerId) {
        Boolean gettingStats = isGettingWebrtcStats.get(peerId);
        if (gettingStats == null) {
            String log = "[SA][WStatsAll] Peer " + peerId +
                    " does not exist. Will not get WebRTC stats.";
            Log.e(TAG, log);
            return;
        }

        if (gettingStats) {
            // Request to get WebRTC stats.
            processGetWebrtcStats(peerId, Info.MEDIA_DIRECTION_BOTH, Info.MEDIA_ALL);

            // Wait for waitMs ms before requesting WebRTC stats again.
            final int waitMs = 1000;
            new Thread(() -> {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    String error =
                            "[SA][WStatsAll] Error while waiting to call for WebRTC stats again: " +
                                    e.getMessage();
                    Log.e(TAG, error);
                }
                processGetWStatsAll(peerId);
            }).start();

        }
    }

    private void processGetWebrtcStats(String peerId, int mediaDirection, int mediaType) {
        multiVideoCallService.getWebrtcStats(peerId, mediaDirection, mediaType);
    }

    private void processAddRemoteView(String remotePeerId) {

        int index = multiVideoCallService.getPeerIndexByPeerId(remotePeerId);

        SurfaceViewRenderer videoView = multiVideoCallService.getVideoView(remotePeerId);

        multiVideoCallView.onPresenterRequestAddRemoteView(index, videoView);
    }

    /**
     * Get the room id info
     */
    private String processGetRoomId() {
        return multiVideoCallService.getRoomId();
    }
}
