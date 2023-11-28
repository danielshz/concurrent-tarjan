import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AdjacencyList {
	private Map<Integer, HashSet<Integer>> in; //in-neighbours
	private Map<Integer, HashSet<Integer>> out; //out-neighbours

	public AdjacencyList(int n) {
		this.in = new HashMap<>();
		this.out = new HashMap<>();

		for(int v = 0; v < n; v++) {
			this.in.put(v, null);
			this.out.put(v, null);
		}
	}

	public void addEdge(int orig, int dest) {
		HashSet<Integer> inSet = this.in.get(dest);

		if(inSet == null) {
			inSet = new HashSet<>();
			this.in.put(dest, inSet);
		}

		inSet.add(orig);

		//updates out-neighbours
		HashSet<Integer> outSet = this.out.get(orig);

		if(outSet == null) {
			outSet = new HashSet<>();
			this.out.put(orig, outSet);
		}

		outSet.add(dest);
	}

	public void showVertices() {
		for(int v = 0; v < this.in.size(); v++) {
			System.out.println(v);
		}
	}

	public void showEdges() {
		for(int v = 0; v < this.out.size(); v++) {
			HashSet<Integer> outSet = this.out.get(v);

			if(outSet != null) {
				for(int elem : this.out.get(v))
					System.out.println(v + " -> " + elem);

				System.out.println();
			}
		}
	}

	public int getNumVertices() {
		return this.in.size();
	}

    public Set<Integer> getVerticesId() {
		HashSet<Integer> vertices = new HashSet<>(this.out.keySet());

		vertices.addAll(this.in.keySet());

        return vertices;
    }

	public HashSet<Integer> getInEdges(int v) {
		return this.in.get(v);
	}

	public HashSet<Integer> getOutEdges(int v) {
		return this.out.get(v);
	}

	public boolean removeOutEdge(int source, int destination) {
		return this.out.get(source).remove(destination);
	}
}