package com.github.faucamp.simplertmp.io;

import android.util.Log;

import com.github.faucamp.simplertmp.packets.Abort;
import com.github.faucamp.simplertmp.packets.Acknowledgement;
import com.github.faucamp.simplertmp.packets.Audio;
import com.github.faucamp.simplertmp.packets.Command;
import com.github.faucamp.simplertmp.packets.Data;
import com.github.faucamp.simplertmp.packets.RtmpHeader;
import com.github.faucamp.simplertmp.packets.RtmpPacket;
import com.github.faucamp.simplertmp.packets.SetChunkSize;
import com.github.faucamp.simplertmp.packets.SetPeerBandwidth;
import com.github.faucamp.simplertmp.packets.UserControl;
import com.github.faucamp.simplertmp.packets.Video;
import com.github.faucamp.simplertmp.packets.WindowAckSize;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author francois
 */
public class RtmpDecoder {
    private static final boolean DEBUG = false;

    private static final String TAG = "RtmpDecoder";

    private RtmpSessionInfo rtmpSessionInfo;

    public RtmpDecoder(RtmpSessionInfo rtmpSessionInfo) {
        this.rtmpSessionInfo = rtmpSessionInfo;
    }

    public RtmpPacket readPacket(InputStream in) throws IOException {

        RtmpHeader header = RtmpHeader.readHeader(in, rtmpSessionInfo);
        // Log.d(TAG, "readPacket(): header.messageType: " + header.getMessageType());

        ChunkStreamInfo chunkStreamInfo = rtmpSessionInfo.getChunkStreamInfo(header.getChunkStreamId());
        chunkStreamInfo.setPrevHeaderRx(header);

        if (header.getPacketLength() > rtmpSessionInfo.getRxChunkSize()) {
            // If the packet consists of more than one chunk,
            // store the chunks in the chunk stream until everything is read
            if (!chunkStreamInfo.storePacketChunk(in, rtmpSessionInfo.getRxChunkSize())) {
                // return null because of incomplete packet
                return null;
            } else {
                // stored chunks complete packet, get the input stream of the chunk stream
                in = chunkStreamInfo.getStoredPacketInputStream();
            }
        }

        RtmpPacket rtmpPacket;
        if (header.getMessageType() == RtmpHeader.MessageType.SET_CHUNK_SIZE) {
            SetChunkSize setChunkSize = new SetChunkSize(header);
            setChunkSize.readBody(in);
            if (DEBUG)
                Log.d(TAG, "readPacket(): Setting chunk size to: " + setChunkSize.getChunkSize());
            rtmpSessionInfo.setRxChunkSize(setChunkSize.getChunkSize());
            return null;
        } else if (header.getMessageType() == RtmpHeader.MessageType.ABORT) {
            rtmpPacket = new Abort(header);
        } else if (header.getMessageType() == RtmpHeader.MessageType.USER_CONTROL_MESSAGE) {
            rtmpPacket = new UserControl(header);
        } else if (header.getMessageType() == RtmpHeader.MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE) {
            rtmpPacket = new WindowAckSize(header);
        } else if (header.getMessageType() == RtmpHeader.MessageType.SET_PEER_BANDWIDTH) {
            rtmpPacket = new SetPeerBandwidth(header);
        } else if (header.getMessageType() == RtmpHeader.MessageType.AUDIO) {
            rtmpPacket = new Audio(header);
        } else if (header.getMessageType() == RtmpHeader.MessageType.VIDEO) {
            rtmpPacket = new Video(header);
        } else if (header.getMessageType() == RtmpHeader.MessageType.COMMAND_AMF0) {
            rtmpPacket = new Command(header);
        } else if (header.getMessageType() == RtmpHeader.MessageType.DATA_AMF0) {
            rtmpPacket = new Data(header);
        } else if (header.getMessageType() == RtmpHeader.MessageType.ACKNOWLEDGEMENT) {
            rtmpPacket = new Acknowledgement(header);
        } else {
            throw new IOException("No packet body implementation for message type: " + header.getMessageType());
        }
        rtmpPacket.readBody(in);
        return rtmpPacket;
    }
}
