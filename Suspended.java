import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class Suspended {
    private HashMap<Search, Search> suspended;
    
    public Suspended() {
        this.suspended = new HashMap<>();
    }

    public synchronized boolean suspend(Search search, Node child) {
        ArrayList<Search> blokingCycle = blokingCycle(child.search, search);
        
        if(blokingCycle == null) {
            suspended.put(search, child.search);
            System.out.println("Sem ciclo ::: s" + search.id);
            return true;
        }

        String ciclo = "";

        for(Search search2 : blokingCycle) {
            ciclo += "s" + search2.id + " -> ";    
        }

        System.out.println("Ciclo: " + ciclo);
        
        System.out.println("Com ciclo ::: s" + search.id);
        transferNodes(search, blokingCycle);
          
        return false;
    }

    public synchronized void unsuspend(Search search) {
        suspended.remove(search);
        search.waitingFor = null;
    }

    private synchronized ArrayList<Search> blokingCycle(Search start, Search target) {
        Search current = start;

        System.out.println(":::::::::::::: b s" + target.id + " : w s" + start.id);
        System.out.println(":::::::::::::: b n" + target.controlStack.peek().id + " : w n" + target.waitingFor.id);
        
        ArrayList<Search> path = new ArrayList<>();
        path.add(current);

        while(suspended.containsKey(current) && current != target) {
            current = suspended.get(current);

            if(current != target)
                path.add(current);
        }

        return current != target ? null : path;
    }

    private synchronized Node getTransferNodes(Node blockerSearch, Search senderSearch, ArrayList<Node> tempTarjan, ArrayList<Node> tempControl) {
        Stack<Node> tarjanStack = senderSearch.tarjanStack;
        Stack<Node> controlStack = senderSearch.controlStack;
            
        Node node = tarjanStack.pop();
        int lowestLowlink = node.lowlink;
        boolean reachedBlocker = node == blockerSearch;
        
        tempTarjan.add(node);

        while(!reachedBlocker || lowestLowlink < node.index) {
            node = tarjanStack.pop();
            tempTarjan.add(0, node);
            lowestLowlink = Math.min(lowestLowlink, node.lowlink);

            if(node == blockerSearch)
                reachedBlocker = true;
        }

        Node oldestNode = node;

        node = controlStack.pop();
        tempControl.add(node);

        while(node.id != oldestNode.id) {
            node = controlStack.pop();
            tempControl.add(0, node);
        }

        return oldestNode;
    }

    private synchronized void transferNodes(Search receiverSearch, ArrayList<Search> blokingPath) {
        Stack<Node> tarjanStack = receiverSearch.tarjanStack;
        Stack<Node> controlStack = receiverSearch.controlStack;

        ArrayList<ArrayList<Search>> blockeds = new ArrayList<>();
        ArrayList<Search> blockers = new ArrayList<>();

        HashMap<Search, Node> waitingNodes = new HashMap<>();

        ArrayList<Search> emptySearches = new ArrayList<>();
        ArrayList<Search> nonEmptySearches = new ArrayList<>();

        Node blockerNode = receiverSearch.waitingFor;

        for(Search senderSearch : blokingPath) {
            ArrayList<Node> tempTarjan = new ArrayList<>();
            ArrayList<Node> tempControl = new ArrayList<>();

            System.out.println(senderSearch.tarjanStack);
            System.out.println(senderSearch.controlStack);

            System.out.println("\n\nSender Control: " + senderSearch.controlStack.peek().id);
            System.out.println("\n\nSender Tarjan: " + senderSearch.tarjanStack.peek().id);

            Node newWaitingFor = getTransferNodes(blockerNode, senderSearch, tempTarjan, tempControl);

            // Atualizando atributos
            Node oldWaitingFor = senderSearch.waitingFor;
            oldWaitingFor.blocked.remove(senderSearch);

            senderSearch.waitingFor = newWaitingFor;
            
            if(!senderSearch.tarjanStack.empty())
                newWaitingFor.blocked.add(senderSearch);
            else
                senderSearch.waitingFor = null;
            
            blockerNode = oldWaitingFor;

            waitingNodes.put(senderSearch, oldWaitingFor);

            // Transferindo nós
            int deltaIndex = receiverSearch.index - newWaitingFor.index;

            ArrayList<Search> allBlocked = new ArrayList<>();

            for(Node node : tempTarjan) {
                node.index += deltaIndex;
                node.lowlink += deltaIndex;
                node.search = receiverSearch;

                for(Search blockedSearch : node.blocked) {
                    if(!allBlocked.contains(blockedSearch))
                        allBlocked.add(0, blockedSearch);
                }

                receiverSearch.index = Math.max(receiverSearch.index, node.index);
                tarjanStack.push(node);
            }

            for(Node node : tempControl)
                controlStack.push(node);

            receiverSearch.index += 1;

            System.out.println("\n\nReceiver Control: " + receiverSearch.controlStack.peek().id);
            System.out.println("\n\nReceiver Tarjan: " + receiverSearch.tarjanStack.peek().id);

            // Pegar as buscas que estão bloqueadas por nós que foram transferidos
            allBlocked.removeIf(search -> search == receiverSearch || blokingPath.contains(search));

            if(!allBlocked.isEmpty()) {
                blockeds.add(allBlocked);
                blockers.add(senderSearch);
            }

            if(senderSearch.tarjanStack.empty())
                emptySearches.add(senderSearch);
            else
                nonEmptySearches.add(senderSearch);
        }

        // Atualizando o suspended
        for(int i = 0; i < blockeds.size(); i++) {
            ArrayList<Search> blockedSearches = blockeds.get(i);
            Search blocker = blockers.get(i);

            while(!blockedSearches.isEmpty()) {
                Search blockedHead = blockedSearches.get(0);
                blockedSearches.remove(0);

                if(suspended.containsKey(blockedHead)) {
                    if(suspended.get(blockedHead) == blocker)
                        suspended.put(blockedHead, receiverSearch);
                }
            }
        }

        for(Search emptySearch : emptySearches) {
            unsuspend(emptySearch);

            Node waitingNode = waitingNodes.get(emptySearch);

            synchronized(emptySearch) {
                System.out.println("(Empty) Liberando " + waitingNode.id + " : s" + emptySearch.id);
                emptySearch.notifyAll();
            }
        }
    
        for(Search nonEmptySearch : nonEmptySearches) {
            Node waitingNode = waitingNodes.get(nonEmptySearch);
            
            synchronized(nonEmptySearch) {
                System.out.println("(Non-Empty) Liberando " + waitingNode.id + " : s" + nonEmptySearch.id);
                nonEmptySearch.notifyAll();
            }

            suspended.put(nonEmptySearch, receiverSearch);
        }
    }
}
