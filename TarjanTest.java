import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class TarjanTest {
    private static final int NTHREADS = 4;
    private static final String GRAPH_PATH = "./grafos/Wiki-Vote.txt";

    private AdjacencyList graphSequential;
    private HashMap<Integer, Node> nodesSequential;
    private ArrayList<Set<Integer>> SCCsSequential;
    private Node startNodeSequential;
    
    private AdjacencyList graphConcurrent;
    private HashMap<Integer, Node> nodesConcurrent;
    private ArrayList<Set<Integer>> SCCsConcurrent;

    @Before
    public void setUp() {
        graphSequential = Program.constructGraph(GRAPH_PATH);
        nodesSequential = Node.getNodeMap(graphSequential.getVerticesId());
        SCCsSequential = new ArrayList<>();
        startNodeSequential = Node.getNotInSCC(nodesSequential, 0);
        
        sequencialTarjan();

        graphConcurrent = Program.constructGraph(GRAPH_PATH);
        nodesConcurrent = Node.getNodeMap(graphConcurrent.getVerticesId());
        SCCsConcurrent = new ArrayList<>();

        concurrentTarjan();
    }

    public void sequencialTarjan() {
        Tarjan tarjan = new Tarjan(graphSequential, nodesSequential);

        Integer nextNode = 0;

        long begin = System.nanoTime();

        do {
            ArrayList<Set<Integer>> result = tarjan.SequentialTarjan(startNodeSequential);
            result.removeAll(SCCsSequential);
            SCCsSequential.addAll(result);

            startNodeSequential = Node.getNotInSCC(nodesSequential, nextNode);
        } while(startNodeSequential != null);

        long end = System.nanoTime();

        System.out.println("Tempo em segundos: " + ((end - begin) * Math.pow(10, -9)));
    }

    public void concurrentTarjan() {
		Scheduler scheduler = new Scheduler(NTHREADS, graphConcurrent, nodesConcurrent);

		long begin = System.nanoTime();

		// Seleção do nó inicial da busca em profundidade
		scheduler.execute();
		scheduler.shutdown();

        SCCsConcurrent = scheduler.getSCCs();

		// for(Set<Integer> SCC : scheduler.getSCCs()) {
		// 	for(int element : SCC) {
		// 		System.out.print("" + element + " ");
		// 	}

		// 	System.out.println("");
		// }

		// System.out.println("");

		long end = System.nanoTime();

		System.out.println("Tempo em segundos: " + ((end - begin) * Math.pow(10, -9)));
	}

    @Test
    public void correctness() {
        long sequencialSize = SCCsSequential.size();
        long concurrentSize = SCCsConcurrent.size();
        
        assertEquals(sequencialSize, concurrentSize);

        for(Set<Integer> SCCSequential : SCCsSequential) {
            boolean found = false;
            
            for(Set<Integer> SCCConcurrent : SCCsConcurrent) {
                if(SCCSequential.equals(SCCConcurrent)) {
                    SCCsConcurrent.remove(SCCConcurrent);
                    found = true;
                    break;
                }
            }

            // if(!found) {
            //     for(Integer nodeId : SCCSequential) {
            //         System.out.println(nodeId);
            //     }
            // }
            
            assertTrue(found);
        }
    }
}
