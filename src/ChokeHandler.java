package src;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import static java.util.stream.Collectors.*;
import java.util.concurrent.TimeUnit;

public class ChokeHandler implements Runnable {
    private int interval;
    private int preferredNeighboursCount;
    private PeerAdmin peerAdmin;
    private Random rand = new Random();
    private ScheduledFuture<?> job = null;
    private ScheduledExecutorService scheduler = null;

    ChokeHandler(PeerAdmin padmin) {
        this.peerAdmin = padmin;
        this.interval = padmin.getUnchockingInterval();
        this.preferredNeighboursCount = padmin.getNoOfPreferredNeighbors();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void startJob() {
        this.job = this.scheduler.scheduleAtFixedRate(this, 6, this.interval, TimeUnit.SECONDS);
    }

    public void run() {
        try {
            HashSet<String> unchokedlist = new HashSet<>(this.peerAdmin.getUnchokedList());
            HashSet<String> newlist = new HashSet<>();
            List<String> interested = new ArrayList<String>(this.peerAdmin.getInterestedList());
            if (interested.size() > 0) {
            	int iter = 0;
            	if(this.preferredNeighboursCount < interested.size()) {
            		iter = this.preferredNeighboursCount;
            	} else {
            		iter = interested.size();
            	}
                if (this.peerAdmin.getCompletedPieceCount() == this.peerAdmin.getPieceCount()) {
                    for (int i = 0; i < iter; i++) {
                        String nextPeer = interested.get(this.rand.nextInt(interested.size()));
                        PeerHandler nextHandler = this.peerAdmin.getPeerHandler(nextPeer);
//                      /for not selecting not selecting same peer which is in newlist
                        while (newlist.contains(nextPeer)) {
                            nextPeer = interested.get(this.rand.nextInt(interested.size()));
                            nextHandler = this.peerAdmin.getPeerHandler(nextPeer);
                        }
                        if (!unchokedlist.contains(nextPeer)) {
                            if (this.peerAdmin.getOptimisticUnchokedPeer() == null
                                    || this.peerAdmin.getOptimisticUnchokedPeer().compareTo(nextPeer) != 0) {
                                this.peerAdmin.getUnchokedList().add(nextPeer);
                                nextHandler.messageSender.sendUnChokedMessage();

                            }
                        }
                        else {
                            unchokedlist.remove(nextPeer);
                        }
                        newlist.add(nextPeer);
                        nextHandler.resetDownloadRate();
                    }
                }
                else {
                    Map<String, Integer> downloads = new HashMap<>(this.peerAdmin.getDownloadRates());
                    Map<String, Integer> rates = downloads.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
                    Iterator<Map.Entry<String, Integer>> iterator = rates.entrySet().iterator();
                    int counter = 0;
                    while (counter < iter && iterator.hasNext()) {

                        Map.Entry<String, Integer> ent = iterator.next();
                        if (interested.contains(ent.getKey())) {
                            PeerHandler nextHandler = this.peerAdmin.getPeerHandler(ent.getKey());
                            if (!unchokedlist.contains(ent.getKey())) {
                                String optUnchoke = this.peerAdmin.getOptimisticUnchokedPeer();
                                if (optUnchoke == null || optUnchoke.compareTo(ent.getKey()) != 0) {
                                    this.peerAdmin.getUnchokedList().add(ent.getKey());
                                    nextHandler.messageSender.sendUnChokedMessage();

                                }
                            }
                            else {
                                unchokedlist.remove(ent.getKey());
                            }
                            newlist.add(ent.getKey());
                            nextHandler.resetDownloadRate();
                            counter++;
                        }
                    }
                }
                for (String peer : unchokedlist) {
                    PeerHandler nextHandler = this.peerAdmin.getPeerHandler(peer);
                    nextHandler.messageSender.sendChokedMessage();
                }
                this.peerAdmin.updateUnchokedList(newlist);
                if(newlist.size() > 0){
                    this.peerAdmin.getClientLogger().updatePreferredNeighbors(new ArrayList<>(newlist));
                }
//                for (String peer : unchokedlist) {
//                    PeerHandler nextHandler = this.peerAdmin.getPeerHandler(peer);
//                    nextHandler.messageSender.sendChokedMessage();
//                }
            }
            else {
                this.peerAdmin.resetUnchokedList();
                unchokedlist.forEach(peer -> {
                	PeerHandler nextHandler = this.peerAdmin.getPeerHandler(peer);
                    nextHandler.messageSender.sendChokedMessage();
                });
                if(this.peerAdmin.checkIfAllPeersAreDone()) {
                    this.peerAdmin.cancelChokes();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelJob() {
        this.scheduler.shutdownNow();
    }
}
