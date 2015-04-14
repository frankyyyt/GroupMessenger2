package edu.buffalo.cse.cse486586.groupmessenger2;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Message Class
 *
 * Created by darrenxyli on 2/26/15.
 * Changed by darrenxyli on 2/26/15 8:57 PM.
 */
public class Message {

    public messageType msgType;
    private long msgId;
    private int msgSource;
    private int msgDes;
    private String msgContent;
    private long msgSeqNum;
    public int msgSeqProcess;

    public static enum messageType {
        PROPOSE, // 1
        VOTE,    // 2
        DECISION, // 3
        NOTIFICATION,
        ACK
    }

    /**
     * Initializer with Message ID and Message Content and Message Source
     *
     * @param msgType messageType
     * @param msgId long
     * @param msgSource String
     * @param msgContent String
     */
    public Message(messageType msgType, long msgId, int msgSource, String msgContent) {
        super();
        this.msgType = msgType;
        this.msgId = msgId;
        this.msgSource = msgSource;
        this.msgContent = msgContent;
        this.msgSeqProcess = Integer.MAX_VALUE;
        this.msgSeqNum = Long.MIN_VALUE;
    }

    /**
     * Get message ID
     * @return id long
     */
    public long getMsgId() {
        return msgId;
    }

    /**
     * Update sequence number of message
     * @param msgSeqNum long
     */
    public void setSeqNumber(long msgSeqNum) {
        this.msgSeqNum = msgSeqNum;
    }

    /**
     * Get sequence number of message
     */
    public long getSeqNumber() {
        return msgSeqNum;
    }

    /**
     * Get message source
     * @return source String
     */
    public int getMsgSource() {
        return msgSource;
    }

    /**
     * Set message source
     * @param source String
     */
    public void setMsgSource(int source) {
        msgSource = source;
    }

    /**
     * Set des
     * @param des
     */
    public void setMsgDestination(int des) {
        msgDes = des;
    }

    /**
     * Get des
     * @return
     */
    public int getMsgDestination() {
        return msgDes;
    }

    /**
     * Set process num of final decision
     * @param process int
     */
    public void setMsgSeqProcess(int process) {
        msgSeqProcess = process;
    }

    /**
     * get the process num of decision
     * @return msgSeqProcess int
     */
    public int getMsgSeqProcess() {
        return msgSeqProcess;
    }

    /**
     * Get message content
     * @return content String
     */
    public String getMsgContent() {
        return msgContent;
    }

    /**
     * Serializable
     * @return message String
     */
    public static String toJSON(Message msg) {
        JSONObject jsonObj = new JSONObject();
        try {
            jsonObj.put("msgType", msg.msgType);
            jsonObj.put("msgId", msg.getMsgId());
            jsonObj.put("msgContent", msg.getMsgContent());
            jsonObj.put("msgSeqNum", msg.getSeqNumber());
            jsonObj.put("msgSeqProcess", msg.getMsgSeqProcess());
            jsonObj.put("msgSource", msg.getMsgSource());
            jsonObj.put("msgDes", msg.getMsgDestination());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObj.toString();
    }

    /**
     * Deseializable
     * @return msg Message
     */
    public static Message fromJSON(String msg) {
        try {
            JSONObject jsonObj = new JSONObject(msg);
            Message message = new Message(
                    messageType.valueOf(String.valueOf(jsonObj.get("msgType"))),
                    Long.parseLong(String.valueOf(jsonObj.get("msgId"))),
                    (int)jsonObj.get("msgSource"),
                    (String)jsonObj.get("msgContent")
            );
            message.setSeqNumber(Long.parseLong(String.valueOf(jsonObj.get("msgSeqNum"))));
            message.setMsgSeqProcess((int)jsonObj.get("msgSeqProcess"));
            message.setMsgDestination((int)jsonObj.get("msgDes"));
            return message;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
