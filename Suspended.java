import java.util.ArrayList;
import java.util.HashMap;

public class Suspended {
    private HashMap<Search, Search> suspended;
    
    public Suspended() {
        this.suspended = new HashMap<>();
    }

    public boolean suspend(Search search, Node child) {
        ArrayList<Search> blokingCycle;
        synchronized(suspended) {
            blokingCycle = blokingCycle(child.search, search);
            
            if(blokingCycle == null) {
                suspended.put(search, child.search);
                System.out.println("Sem ciclo ::: s" + search.id);
                return true;
            }
        }

        String ciclo = "";

        for(Search search2 : blokingCycle) {
            ciclo += "s" + search2.id + " -> ";    
        }

        System.out.println("Ciclo: " + ciclo);
        
        System.out.println("Com ciclo ::: s" + search.id);
        transfer(search, blokingCycle);
          
        return false;
    }

    public void unsuspend(Search search) {
        synchronized(suspended) {
            suspended.remove(search);
        }
    }

    private ArrayList<Search> blokingCycle(Search start, Search target) {
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

    private void transfer(Search receiverSearch, ArrayList<Search> blokingPath) {
        ArrayList<ArrayList<Search>> blockeds = new ArrayList<>();
        ArrayList<Search> blockers = new ArrayList<>();

        ArrayList<Search> emptySearches = new ArrayList<>();
        ArrayList<Search> nonEmptySearches = new ArrayList<>();

        Node blockerNode = receiverSearch.waitingFor;

        for(Search senderSearch : blokingPath) {
            ArrayList<Node> tempTarjan = new ArrayList<>();
            ArrayList<Node> tempControl = new ArrayList<>();

            Node oldWaitingFor = senderSearch.getTransferNodes(blockerNode, tempTarjan, tempControl);
            
            // Armazendo buscas com pilhas vazias e não vazias para desbloqueio posterior
            if(senderSearch.tarjanStack.empty())
                emptySearches.add(senderSearch);
            else
                nonEmptySearches.add(senderSearch);    
            
            // Definição do nó que bloqueia a busca da próxima iteração do ciclo de bloqueio
            blockerNode = oldWaitingFor;

            // Transferindo nós para a nova busca
            ArrayList<Search> allBlocked = receiverSearch.transferNodes(tempTarjan, tempControl);

            // Pegar as buscas que estão bloqueadas por nós que foram transferidos
            allBlocked.removeIf(search -> search == receiverSearch || blokingPath.contains(search));

            if(!allBlocked.isEmpty()) {
                blockeds.add(allBlocked);
                blockers.add(senderSearch);
            }
        }

        // Atualizando o suspended das buscas cujos nós foram transferidos para outra busca
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

        synchronized(suspended) {
            // Liberando as buscas com pilhas vazias
            for(Search emptySearch : emptySearches) {
                synchronized(emptySearch) {
                    System.out.println("(Empty) Liberando : s" + emptySearch.id);
                    unsuspend(emptySearch);
                    emptySearch.notifyAll();
                }
            }
            
            // Liberando as buscas com pilhas não vazias
            for(Search nonEmptySearch : nonEmptySearches) {
                synchronized(nonEmptySearch) {
                    System.out.println("(Non-Empty) Liberando : s" + nonEmptySearch.id);
                    suspended.put(nonEmptySearch, receiverSearch);
                    // nonEmptySearch.notifyAll();
                }
            }
        }

    }
}
