package com.dds.skywebrtc.engine.webrtc;

import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dds on 2020/3/11.
 * android_shuai@163.com
 */
public class Peer implements SdpObserver, PeerConnection.Observer {
    private final static String TAG = "dds_Peer";
    private PeerConnection pc;
    private String mUserId;
    private List<IceCandidate> queuedRemoteCandidates;
    private SessionDescription localSdp;
    private PeerConnectionFactory mFactory;
    private List<PeerConnection.IceServer> mIceLis;
    private IPeerEvent mEvent;
    public MediaStream _remoteStream;
    private WebRTCEngine mWebRTCEngine;
    private boolean isOffer;

    public Peer(WebRTCEngine webRTCEngine, PeerConnectionFactory factory, List<PeerConnection.IceServer> list, String userId, IPeerEvent event) {
        mWebRTCEngine = webRTCEngine;
        mFactory = factory;
        mIceLis = list;
        mEvent = event;
        mUserId = userId;
        queuedRemoteCandidates = new ArrayList<>();
        this.pc = createPeerConnection();
        Log.d("dds_test", "create Peer:" + mUserId);

    }

    public PeerConnection createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(mIceLis);
        return mFactory.createPeerConnection(rtcConfig, this);
    }

    public void setOffer(boolean isOffer) {
        this.isOffer = isOffer;
    }

    // 创建offer
    public void createOffer() {
        if (pc == null) return;
        Log.d("dds_test", "createOffer");
        pc.createOffer(this, offerOrAnswerConstraint());
    }

    // 创建answer
    public void createAnswer() {
        if (pc == null) return;
        Log.d("dds_test", "createAnswer");
        pc.createAnswer(this, offerOrAnswerConstraint());

    }

    public void setLocalDescription(SessionDescription sdp) {
        Log.d("dds_test", "setLocalDescription");
        if (pc == null) return;
        pc.setLocalDescription(this, sdp);
    }

    public void setRemoteDescription(SessionDescription sdp) {
        if (pc == null) return;
        Log.d("dds_test", "setRemoteDescription");
        pc.setRemoteDescription(this, sdp);
    }

    public void addLocalStream(MediaStream stream) {
        if (pc == null) return;
        Log.d("dds_test", "addLocalStream" + mUserId);
        pc.addStream(stream);
    }


    public void addRemoteIceCandidate(final IceCandidate candidate) {
        Log.d("dds_test", "addRemoteIceCandidate");
        if (pc != null) {
            if (queuedRemoteCandidates != null) {
                Log.d("dds_test", "addRemoteIceCandidate  2222");
                queuedRemoteCandidates.add(candidate);
            } else {
                Log.d("dds_test", "addRemoteIceCandidate1111");
                pc.addIceCandidate(candidate);
            }
        }
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        if (pc == null) {
            return;
        }
        drainCandidates();
        pc.removeIceCandidates(candidates);
    }


    public void close() {
        if (pc != null) {
            pc.close();
            pc.dispose();
        }
    }

    public MediaStream getRemoteStream() {
        return _remoteStream;
    }

    //------------------------------Observer-------------------------------------
    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.i(TAG, "onSignalingChange: " + signalingState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {

    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        Log.i(TAG, "onIceConnectionReceivingChange:" + receiving);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        Log.i(TAG, "onIceGatheringChange:" + newState.toString());
    }


    @Override
    public void onIceCandidate(IceCandidate candidate) {
        // 发送IceCandidate
        mEvent.onSendIceCandidate(mUserId, candidate);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        Log.i(TAG, "onIceCandidatesRemoved:");
    }

    @Override
    public void onAddStream(MediaStream stream) {
        Log.i(TAG, "onAddStream:");
        stream.audioTracks.get(0).setEnabled(true);
        _remoteStream = stream;
        if (mEvent != null) {
            mEvent.onRemoteStream(mUserId, stream);
        }
    }

    @Override
    public void onRemoveStream(MediaStream stream) {
        Log.i(TAG, "onRemoveStream:");
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.i(TAG, "onDataChannel:");
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.i(TAG, "onRenegotiationNeeded:");
    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
        Log.i(TAG, "onAddTrack:" + mediaStreams.length);
    }


    //-------------SdpObserver--------------------
    @Override
    public void onCreateSuccess(SessionDescription origSdp) {
        Log.d(TAG, "sdp创建成功       " + origSdp.type);
        String sdpString = origSdp.description;
        final SessionDescription sdp = new SessionDescription(origSdp.type, sdpString);
        localSdp = sdp;
        setLocalDescription(sdp);

    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, "sdp连接成功   " + pc.signalingState().toString());
        if (pc == null) return;
        // 发送者
        if (isOffer) {
            if (pc.getRemoteDescription() == null) {
                Log.d(TAG, "Local SDP set succesfully");
                if (!isOffer) {
                    //接收者，发送Answer
                    mEvent.onSendAnswer(mUserId, localSdp);
                } else {
                    //发送者,发送自己的offer
                    mEvent.onSendOffer(mUserId, localSdp);
                }
            } else {
                Log.d(TAG, "Remote SDP set succesfully");

                drainCandidates();
            }

        } else {
            if (pc.getLocalDescription() != null) {
                Log.d(TAG, "Local SDP set succesfully");
                if (!isOffer) {
                    //接收者，发送Answer
                    mEvent.onSendAnswer(mUserId, localSdp);
                } else {
                    //发送者,发送自己的offer
                    mEvent.onSendOffer(mUserId, localSdp);
                }

                drainCandidates();
            } else {
                Log.d(TAG, "Remote SDP set succesfully");
            }
        }


    }

    @Override
    public void onCreateFailure(String error) {
        Log.i(TAG, " SdpObserver onCreateFailure:" + error);
    }

    @Override
    public void onSetFailure(String error) {
        Log.i(TAG, "SdpObserver onSetFailure:" + error);
    }


    private void drainCandidates() {
        Log.i("dds_test", "drainCandidates");
        if (queuedRemoteCandidates != null) {
            Log.d(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");
            for (IceCandidate candidate : queuedRemoteCandidates) {
                pc.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
        }
    }

    private MediaConstraints offerOrAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    // ----------------------------回调-----------------------------------

    public interface IPeerEvent {
        void onSendIceCandidate(String userId, IceCandidate candidate);

        void onSendOffer(String userId, SessionDescription description);

        void onSendAnswer(String userId, SessionDescription description);

        void onRemoteStream(String userId, MediaStream stream);

    }

}
