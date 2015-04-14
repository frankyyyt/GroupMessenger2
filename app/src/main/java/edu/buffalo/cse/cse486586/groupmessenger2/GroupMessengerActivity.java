package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author darrenxyli
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();


    private MessageUtil mUtil;
    private Lock lock;
    private Lock lockOfRemoteList;
    private Lock saveNumLock;
    private Handler handler;

    /**
     * holdBackQueue
     *
     * @description hold-back queue will hold messages until it gets the sequence number assigned,
     * designed in priority queue
     */
    private PriorityBlockingQueue<QueueItem> holdBackQueue ;

    /**
     * holdBackQueue Comparator
     */
    Comparator<QueueItem> holdBackQueueC = new Comparator<QueueItem>() {
        @Override
        public int compare(QueueItem msg1, QueueItem msg2) {
            if (!msg1.isDeliverable() && msg2.isDeliverable()) {
                return -1;
            } else if (msg1.isDeliverable() && !msg2.isDeliverable()) {
                return 1;
            } else {
                int c = (int) (msg1.msgSeqNum - msg2.msgSeqNum);
                if (c == 0) {
                    return msg1.msgSeqProcess - msg2.msgSeqProcess;
                } else {
                    return c;
                }
            }
        }
    };

    /*
     * localSendingMsgCounter is the largest agreed sequence number which current process
     * holds observed for whole communication group
     */
    private long localSendingMsgCounter;

    /*
     * maxProposedSeqNum is the current process own largest proposed sequence number
     */
    private long maxProposedSeqNum;

    /*
     * lcoalSaveSeq
     */
    private int localSaveSeq;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        holdBackQueue = new PriorityBlockingQueue<>(30, holdBackQueueC);
        localSendingMsgCounter = 0;
        maxProposedSeqNum = -1;
        localSaveSeq = -1;
        lock = new ReentrantLock();
        lockOfRemoteList = new ReentrantLock();
        saveNumLock = new ReentrantLock();
        handler = new Handler();

        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        mUtil = new MessageUtil(Integer.parseInt(myPort));

        /*
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);

        findViewById(R.id.button4).setOnClickListener(
            new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    String msgContent = editText.getText().toString() + "\n";
                    editText.setText("");
                    Message myMessage = new Message(
                            Message.messageType.PROPOSE,
                            getSendMsgId(),
                            mUtil.DEVICEPORT,
                            msgContent
                    );
                    mutilCast(myMessage);
                }
            }
        );

        /*
         * Create a server socket as well as a thread (AsyncTask) that listens on the server
         * port.
         *
         * AsyncTask is a simplified thread construct that Android provides. Please make sure
         * you know how it works by reading
         * http://developer.android.com/reference/android/os/AsyncTask.html
         */
        try {
            ServerSocket serverSocket = new ServerSocket(MessageUtil.SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    /**
     * getReplyProposedSeqNum
     *
     * @return proposedSeqNum long
     * @description current process reply other process with a proposed sequence number P, which
     * P = MAX(maxProposedSeqNum, maxAgreedSeqNum) + 1; Update the maxProposedSeqNum at same time
     */
    public long getReplyProposedSeqNum() {
        try {
            if(lock.tryLock(500, TimeUnit.MILLISECONDS)){
                maxProposedSeqNum ++;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            long temp = maxProposedSeqNum;
            lock.unlock();
            return temp;
        }
    }

    public int getLocalSaveSeq() {
        try {
            if(saveNumLock.tryLock(500, TimeUnit.MILLISECONDS)) {
                localSaveSeq ++;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            int temp = localSaveSeq;
            saveNumLock.unlock();
            return temp;
        }
    }

    /**
     * getSendMsgId
     *
     * @return sendMsgId long
     * @description give back a msg Id for sender
     */
    public long getSendMsgId() {
        return ++localSendingMsgCounter;
    }

    /**
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author darrenxyli
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            Log.d(TAG, "ServerTask Starts");
            ServerSocket serverSocket = sockets[0];
            Socket clientSocket = null;

            do {
                try {
                    clientSocket = serverSocket.accept();
                    new Thread(new ServerThread(clientSocket)).start();
                } catch (Exception e) {
                    Log.e(TAG, "Exception caught when trying to listen on port or listening for a connection");
                    Log.e(TAG, e.toString());
                }
            } while (!clientSocket.isInputShutdown());

            return null;
        }
    }

    /**
     * ServerThread
     */
    public class ServerThread implements Runnable {
        Socket socket = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        public ServerThread(Socket s) throws IOException {
            this.socket = s;
            this.in = new DataInputStream(s.getInputStream());
            this.out = new DataOutputStream(s.getOutputStream());
        }

        @Override
        public void run() {
            String msgReceived = null;
            try {

                socket.setSoTimeout(MessageUtil.TIMEOUT);
                try {
                    msgReceived = in.readUTF();
                } catch (SocketTimeoutException e) {
                    Log.e("Client " + mUtil.DEVICEPORT, "The socket in server thread is timeout in reading propose or decision");
                }

                if (msgReceived != null){

                    Log.d("Client " + mUtil.DEVICEPORT, "Received: " + msgReceived);
                    Message message = Message.fromJSON(msgReceived);

                    if (message.msgType == Message.messageType.PROPOSE) {
                        /**
                         * si :=si +1;
                         * Send <mid,si> to pj;
                         * Put <m,mid, j, si, i, undeliverable> in hold-back queue;
                         */

                        QueueItem item = new QueueItem(message.getMsgId(), message.getMsgContent(), message.getMsgSource());
                        Long proposedID = getReplyProposedSeqNum();


                        if (searchQueue(item)) {
                            Log.d("Client " + mUtil.DEVICEPORT, "Already in queue: " + msgReceived);
                        } else {
                            holdBackQueue.offer(item);
                            Log.d("Client " + mUtil.DEVICEPORT, "Enqueue: " + msgReceived);
                        }

                        // Send back proposed ID
                        message.msgType = Message.messageType.VOTE;
                        message.setSeqNumber(proposedID);
                        message.setMsgSeqProcess(mUtil.DEVICEPORT);
                        message.setMsgDestination(item.originSource);
                        message.setMsgSource(mUtil.DEVICEPORT);

                        out.writeUTF(Message.toJSON(message));
                        out.flush();

                        Log.d("Client " + mUtil.DEVICEPORT, "Vote sent back: " + Message.toJSON(message));

                    } else {
                        if (message.msgType == Message.messageType.DECISION) {

                            /**
                             * On B-deliver of message <mid, i, sk, k> (where 1 ≤ k ≤ n)
                             * si := max(si,sk)
                             * Modify message with id <mid, i> on hold-back queue as follows:
                             *  change proposed sequence number to sk;
                             *  change process that suggested sequence number to k; change undeliverable to deliverable;
                             */

                            long ackSeqNum = message.getSeqNumber();

                            try {
                                if (lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                                    maxProposedSeqNum = maxProposedSeqNum > ackSeqNum ? maxProposedSeqNum : ackSeqNum;
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } finally {
                                lock.unlock();
                            }

                            Iterator<QueueItem> iterator = holdBackQueue.iterator();
                            QueueItem iterated = null;
                            while (iterator.hasNext()) {
                                iterated = iterator.next();
                                if (message.getMsgId() == iterated.msgID && message.getMsgSource() == iterated.originSource) {
                                    iterator.remove();
                                    break;
                                }
                            }

                            if (iterated != null) {
                                iterated.msgSeqNum = message.getSeqNumber();
                                iterated.msgSeqProcess = message.msgSeqProcess;
                                iterated.setMsgStatus(true);
                                holdBackQueue.offer(iterated);
                            }

                            message.msgType = Message.messageType.ACK;
                            out.writeUTF(Message.toJSON(message));
                            out.flush();

                            handler.postDelayed(deliveryTask, 2000);
                            //new DeliveryTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                        } else if (message.msgType == Message.messageType.NOTIFICATION) {
                            int port = message.getMsgSeqProcess();
                            cleanDead(port);
                        } else if (message.msgType == Message.messageType.ACK) {
                            message.setMsgDestination(message.getMsgSource());
                            message.setMsgSource(mUtil.DEVICEPORT);
                            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                            out.writeUTF(Message.toJSON(message));
                            out.flush();
                        }
                    }
                }
            } catch (IOException e) {
                Log.d("Client " + mUtil.DEVICEPORT, "Socket IO exception in Server Thread.");
                e.printStackTrace();
            }
        }
    }

    private Runnable deliveryTask = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "DeliveryTask Starts");
            QueueItem msgReceived = delivery();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);

            while (msgReceived != null) {
                Log.d("Client " + mUtil.DEVICEPORT, "Ready to delivery: " + msgReceived.msgContent);
                remoteTextView.append(msgReceived.originSource + " says: " + msgReceived.msgContent + "\t\n");

                ContentValues cv = new ContentValues();
                cv.put(MessagesSchema.MessageEntry.COLUMN_NAME_KEY, getLocalSaveSeq());
                cv.put(MessagesSchema.MessageEntry.COLUMN_NAME_VALUE, msgReceived.msgContent);
                getContentResolver().insert(MessageUtil.DATABASE_CONTENT_URL, cv);
                msgReceived = delivery();
            }

            Log.d(TAG, "DeliveryTask Finished");
        }
    };

    /**
     * DeliveryTask is an AsyncTask that should handle delivery messages.
     *
     * @author darrenxyli
     */
    private class DeliveryTask extends AsyncTask<Void, QueueItem, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(TAG, "DeliveryTask Starts");
            QueueItem msg = delivery();
            while (msg != null) {
                Log.d("Client " + mUtil.DEVICEPORT, "Ready to delivery: " + msg.msgContent);
                publishProgress(msg);
                msg = delivery();
            }

            Log.d(TAG, "DeliveryTask Finished");
            return null;
        }

        protected void onProgressUpdate(QueueItem...messages) {
            QueueItem msgReceived = messages[0];
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);

            if (mUtil.REMOTEPORTS.contains(msgReceived.originSource)) {
                remoteTextView.append(msgReceived.originSource + " says: " + msgReceived.msgContent + "\t\n");

                ContentValues cv = new ContentValues();
                cv.put(MessagesSchema.MessageEntry.COLUMN_NAME_KEY, getLocalSaveSeq());
                cv.put(MessagesSchema.MessageEntry.COLUMN_NAME_VALUE, msgReceived.msgContent);
                getContentResolver().insert(MessageUtil.DATABASE_CONTENT_URL, cv);
            } else {
                remoteTextView.append(msgReceived.originSource + "(DEAD) says: " + msgReceived.msgContent + "\t\n");
            }
        }
    }

    /**
     * delivery
     *
     * @description delivery the items in queue
     */
    public QueueItem delivery() {
        if (!holdBackQueue.isEmpty()) {
            QueueItem msg = holdBackQueue.peek();
            if(msg.isDeliverable()) {
                holdBackQueue.remove(msg);
                return msg;
            }
        }
        return null;
    }

    public boolean searchQueue(QueueItem item) {
        Iterator<QueueItem> iterator = holdBackQueue.iterator();
        QueueItem iterated;
        while (iterator.hasNext()) {
            iterated = iterator.next();
            if (item.msgID == iterated.msgID && item.originSource == iterated.originSource) {
                return true;
            }
        }
        return false;
    }

    /**
     * mutilCast
     * @param msg Message
     */
    public void mutilCast(Message msg) {
        int remotePort;
        // Send itself firstly
        if (msg.msgType == Message.messageType.PROPOSE) {
            msg.setMsgDestination(mUtil.DEVICEPORT);
            QueueItem item = new QueueItem(msg.getMsgId(), msg.getMsgContent(), msg.getMsgSource());
            holdBackQueue.offer(item);
            Log.d("Client " + mUtil.DEVICEPORT, "Enqueue self: " + Message.toJSON(msg));
        }

        // Then send others
        for(int i=0; i<mUtil.REMOTEPORTS.size(); i++) {
            remotePort = mUtil.REMOTEPORTS.get(i);
            msg.setMsgDestination(remotePort);
            String temp = Message.toJSON(msg);
            new UniCastTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, temp, String.valueOf(remotePort));
        }
    }

    /**
     * UniCastTask
     */
    private class UniCastTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String msg = msgs[0];
            String des = msgs[1];
            Message message = Message.fromJSON(msg);
            final int remotePort = Integer.parseInt(des);

            try {
                InetSocketAddress sockaddr = new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);

                if (message.msgType == Message.messageType.PROPOSE) {
                    Socket socket = new Socket();
                    socket.setSoTimeout(MessageUtil.TIMEOUT);
                    socket.connect(sockaddr, MessageUtil.TIMEOUT);

                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    out.writeUTF(msg);
                    out.flush();
                    Log.d("Client " + mUtil.DEVICEPORT, "Sending to port:" + des + "  " + msg);

                    String needVote = null;
                    try {
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        needVote = in.readUTF();
                    } catch (SocketTimeoutException | SocketException e) {
                        Log.e("Client " + mUtil.DEVICEPORT, "The socket of " + remotePort + " is timeout in reading vote");
                        cleanDead(remotePort);
                    } catch (IOException e) {
                        Log.e("Client " + mUtil.DEVICEPORT, "The IO exception in receiving vote");
                        cleanDead(remotePort);
                    }

                    if (needVote != null)
                    {
                        Log.d("Client " + mUtil.DEVICEPORT, "Received Vote: " + needVote);
                        Message inMsg = Message.fromJSON(needVote);
                        if (inMsg.msgType == Message.messageType.VOTE) {

                            /**
                             * On receive(<mid,sj>) from pj (where 1 ≤ j ≤ n)
                             * Add <sj,j> to list of suggested sequence numbers for message mid;
                             * if we have received sequence number from all processes in g then
                             *  <sk,k> := highest sequence number in list (suggested by pk);
                             *  // choose smallest possible value for k if there are multiple suggesting this sequence # B-multicast(<mid, i, sk, k>);
                             * end-if
                             */
                            Iterator<QueueItem> iterator = holdBackQueue.iterator();
                            QueueItem iterated = null;
                            while (iterator.hasNext()) {
                                iterated = iterator.next();
                                if (inMsg.getMsgId() == iterated.msgID && inMsg.getMsgDestination() == iterated.originSource) {
                                    iterator.remove();
                                    break;
                                }
                            }
                            QueueItem result = iterated;

                            if (result != null) {
                                result.addVote(new Vote(inMsg.getMsgSeqProcess(), inMsg.getSeqNumber()));
                                holdBackQueue.offer(result);
                                Log.d("QQQQ", ""+mUtil.REMOTEPORTS.size());
                                if (result.getElectionProcess().size() >= mUtil.REMOTEPORTS.size()) {
                                    Vote finalVote = result.getMaxVote();
                                    result.msgSeqNum = finalVote.voteSeqNum;
                                    result.msgSeqProcess = finalVote.voteProcess;

                                    inMsg.msgType = Message.messageType.DECISION;
                                    inMsg.setSeqNumber(result.msgSeqNum);
                                    inMsg.setMsgSeqProcess(result.msgSeqProcess);
                                    inMsg.setMsgSource(mUtil.DEVICEPORT);
                                    mutilCast(inMsg);
                                }
                            } else {
                                Log.d("Client " + mUtil.DEVICEPORT, "It isn't in queue: " + needVote);
                            }
                        }
                    } else {
                        cleanDead(remotePort);
                    }
                    socket.close();
                } else if (message.msgType == Message.messageType.DECISION) {
                    Socket socket = new Socket();
                    socket.setSoTimeout(MessageUtil.TIMEOUT);
                    socket.connect(sockaddr, MessageUtil.TIMEOUT);

                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    out.writeUTF(msg);
                    out.flush();

                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    String res = in.readUTF();

                    if (res != null) {
                        Log.d("Client " + mUtil.DEVICEPORT, "Received ACK of " + res);
                    }
                    socket.close();
                } else {
                    Socket socket = new Socket();
                    socket.setSoTimeout(MessageUtil.TIMEOUT);
                    socket.connect(sockaddr, MessageUtil.TIMEOUT);

                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    out.writeUTF(msg);
                    out.flush();
                    out.close();
                    socket.close();
                }
                return null;

            } catch (UnknownHostException e) {
                Log.e("Client " + mUtil.DEVICEPORT, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e("Client " + mUtil.DEVICEPORT, "ClientTask socket IOException");
                Log.e("Client " + mUtil.DEVICEPORT, e.toString());
                cleanDead(remotePort);
            }

            return null;
        }
    }

    /**
     * Clean the deadPort
     */
    public void cleanDead(int port) {

        lockOfRemoteList.lock();
        try {
            if (mUtil.REMOTEPORTS.contains(port)) {
                int i = mUtil.REMOTEPORTS.indexOf(port);
                mUtil.REMOTEPORTS.remove(i);
                Log.d("Client " + mUtil.DEVICEPORT, "" + mUtil.REMOTEPORTS.size());

                Log.d("Client " + mUtil.DEVICEPORT, "clean process finished " + port);

                Iterator<QueueItem> queueItemIterator = holdBackQueue.iterator();
                QueueItem iterated;
                while (queueItemIterator.hasNext()) {
                    iterated = queueItemIterator.next();
                    if (iterated.originSource == port) {
                        queueItemIterator.remove();
                        continue;
                    } else {
                        iterated.cleanElecProcess(port);
//                        if (iterated.getElectionProcess().size() >= mUtil.REMOTEPORTS.size()) {
//                            Message decision = new Message(Message.messageType.DECISION,getSendMsgId(),mUtil.DEVICEPORT,"");
//                            decision.setSeqNumber(iterated.getMaxVote().voteSeqNum);
//                            decision.setMsgSeqProcess(iterated.getMaxVote().voteProcess);
//                            mutilCast(decision);
//                        }
                    }
                }
            }
        } finally {
            lockOfRemoteList.unlock();
        }
    }
}
