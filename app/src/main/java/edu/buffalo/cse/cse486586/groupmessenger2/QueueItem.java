package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GroupMessenger
 * <p/>
 * Created by darrenxyli on 3/10/15.
 * Changed by darrenxyli on 3/10/15 9:49 PM.
 */
public class QueueItem {
    public long msgID;
    public String msgContent;
    public int originSource;
    public long msgSeqNum = -15;
    public int msgSeqProcess = Integer.MAX_VALUE;
    private boolean status = false;
    private ArrayList<Vote> electionProcess = new ArrayList<>();

    private Lock lock = new ReentrantLock();

    private static final class voteComparator implements Comparator<Vote> {

        @Override
        public int compare(Vote e1, Vote e2) {
            if (e1.voteSeqNum > e2.voteSeqNum) {
                return -1;
            } else if (e1.voteSeqNum < e2.voteSeqNum) {
                return 1;
            } else {
                return e1.voteProcess - e2.voteProcess;
            }
        }
    }

    public QueueItem(long id, String content, int source) {
        this.msgID = id;
        this.msgContent = content;
        this.originSource = source;
        this.status = false;
    }

    /**
     * Get election process
     *
     * @return electionProcess Map<>
     */
    public Vote getMaxVote() {
        lock.lock();
        try {
            Collections.sort(electionProcess, new voteComparator());
            return electionProcess.get(0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Add vote
     *
     * @param vote Vote
     */
    public void addVote(Vote vote) {
        lock.lock();
        try {
            electionProcess.add(vote);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get election process
     *
     * @return electionProcess ArrayList
     */
    public ArrayList<Vote> getElectionProcess() {
        return electionProcess;
    }

    /**
     * Check deliverable status
     *
     * @return deliveryStatus boolean
     */
    public boolean isDeliverable() {
        return status;
    }

    /**
     * Update delever status of Message
     *
     * @param status boolean
     */
    public void setMsgStatus(boolean status) {
        this.status = status;
    }

    /**
     * Clean electionProcess
     * @param port int
     */
    public void cleanElecProcess(int port) {
        lock.lock();
        try {
            ArrayList<Vote> vList = electionProcess;
            Iterator<Vote> voteIterator = vList.iterator();
            Vote v;

            while (voteIterator.hasNext()) {
                v = voteIterator.next();
                if (v.voteProcess == port) {
                    voteIterator.remove();
                    continue;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * TEST
     */
    public static void main(String [] args) {
        QueueItem item = new QueueItem(1, "a", 1);
        item.addVote(new Vote(1, 100));
        item.addVote(new Vote(2, 40));
        item.addVote(new Vote(3, 30));
        item.addVote(new Vote(4, 20));
        item.addVote(new Vote(5, 10));
        System.out.println(item.getElectionProcess().size());
        System.out.println(item.getMaxVote().voteSeqNum + "||" + item.getMaxVote().voteProcess);
    }
}
