
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class TarjanTest {
    private static final int NTHREADS = 4;
    private static final String GRAPH_PATH = "./grafos/facebook_combined.txt";
    private static boolean setUpIsDone = false;

    private static AdjacencyList graphSequential;
    private static HashMap<Integer, Node> nodesSequential;
    private static ArrayList<Set<Integer>> SCCsSequential;
    private static Node startNodeSequential;
    
    private static AdjacencyList graphConcurrent;
    private static HashMap<Integer, Node> nodesConcurrent;
    private static ArrayList<Set<Integer>> SCCsConcurrent;

    public static void sequencialTarjan() {
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

        System.out.println("Tempo em segundos (Sequencial): " + ((end - begin) * Math.pow(10, -9)));
    }

    public static void concurrentTarjan() {
        Scheduler scheduler = new Scheduler(NTHREADS, graphConcurrent, nodesConcurrent);

        long begin = System.nanoTime();

        scheduler.execute();
        scheduler.shutdown();

        long end = System.nanoTime();
        
        SCCsConcurrent = scheduler.getSCCs();

        System.out.println("Tempo em segundos (Concorrente): " + ((end - begin) * Math.pow(10, -9)));
    }

    @BeforeClass
    public static void setUp() {
        if(setUpIsDone) {
            return;
        }

        graphSequential = Program.constructGraph(GRAPH_PATH);
        nodesSequential = Node.getNodeMap(graphSequential.getVerticesId());
        SCCsSequential = new ArrayList<>();
        startNodeSequential = Node.getNotInSCC(nodesSequential, 0);
        
        sequencialTarjan();

        graphConcurrent = Program.constructGraph(GRAPH_PATH);
        nodesConcurrent = Node.getNodeMap(graphConcurrent.getVerticesId());
        SCCsConcurrent = new ArrayList<>();

        concurrentTarjan();

        setUpIsDone = true;
    }

    @Test
    public void allSequentialNodesCompleted() {
        for(Node node : nodesSequential.values()) {
            assertEquals(Node.Status.COMPLETE, node.status);
        }
    }

    @Test
    public void allSequentialEdgesExplored() {
        for(Node node : nodesSequential.values()) {
            Set<Integer> outNeighbours = graphSequential.getOutEdges(node.id);

            assertTrue(outNeighbours == null || outNeighbours.isEmpty());
        }
    }

    @Test
    public void allConcurrentNodesCompleted() {
        for(Node node : nodesConcurrent.values()) {
            assertEquals(Node.Status.COMPLETE, node.status);
        }
    }

    @Test
    public void allConcurrentEdgesExplored() {
        for(Node node : nodesConcurrent.values()) {
            Set<Integer> outNeighbours = graphConcurrent.getOutEdges(node.id);

            assertTrue(outNeighbours == null || outNeighbours.isEmpty());
        }
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
            
            assertTrue(found);
        }
    }
}
