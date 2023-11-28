import java.util.ArrayList;
import java.util.HashMap;

public class Suspended {
    private HashMap<Search, Search> suspended;
    
    public Suspended() {
        this.suspended = new HashMap<>();
    }

    // Verifica se há ciclo de bloqueio entre as buscas e, se não houver, adiciona a busca ao HashMap suspended
    // Retorna true se a busca foi suspensa e false caso haja ciclo de bloqueio e os nós das outras buscas tenham sido transferidos
    public boolean suspend(Search search, Node child) {
        ArrayList<Search> blokingCycle;

        synchronized(suspended) {
            blokingCycle = blokingCycle(child.search, search);
            
            if(blokingCycle == null) {
                suspended.put(search, child.search);
                return true;
            }
        }

        transfer(search, blokingCycle);
          
        return false;
    }

    // Remove a busca do HashMap suspended
    public void unsuspend(Search search) {
        synchronized(suspended) {
            suspended.remove(search);
        }
    }

    // Retorna o ciclo de bloqueio, se existir, percorrendo as buscas bloqueadas pelo HashMap suspended
    private ArrayList<Search> blokingCycle(Search start, Search target) {
        Search current = start;
        
        ArrayList<Search> path = new ArrayList<>();
        path.add(current);

        while(suspended.containsKey(current) && current != target) {
            current = suspended.get(current);

            if(current != target)
                path.add(current);
        }

        return current != target ? null : path;
    }
    
    // Transfere os nós das buscas contidas no ciclo de bloqueio para uma nova busca que também está no ciclo
    private void transfer(Search receiverSearch, ArrayList<Search> blokingPath) {
        ArrayList<ArrayList<Search>> blockeds = new ArrayList<>();
        ArrayList<Search> blockers = new ArrayList<>();

        ArrayList<Search> emptySearches = new ArrayList<>();
        ArrayList<Search> nonEmptySearches = new ArrayList<>();

        Node blockerNode = receiverSearch.waitingFor;

        for(Search senderSearch : blokingPath) {
            ArrayList<Node> tempTarjan = new ArrayList<>();
            ArrayList<Node> tempControl = new ArrayList<>();

            // Pegando o nó que bloqueia a busca atual
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
                    unsuspend(emptySearch);
                    emptySearch.notifyAll();
                }
            }
            
            // Liberando as buscas com pilhas não vazias
            for(Search nonEmptySearch : nonEmptySearches) {
                synchronized(nonEmptySearch) {
                    suspended.put(nonEmptySearch, receiverSearch);
                }
            }
        }

    }
}
