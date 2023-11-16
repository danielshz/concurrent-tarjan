import java.util.Set;
import java.util.Stack;

import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

public class Program {
	private static Boolean[] visited;
	private static Stack<Integer> stack;

	public static void main(String[] args) {
		String path = "";

		if(args[0] == null) {
			System.out.println("Falta argumentos! Digite <nome do arquivo>");
			System.exit(0);
		} else {
			path = args[0];
		}

		//inicializa o grafo
		AdjacencyList g = constructGraph(path);
		
		visited = new Boolean[g.getNumVertices()];

		for(int i = 0; i < g.getNumVertices(); i++)
			visited[i] = false;

		// Executa DFS iterativa
		Stack<Integer> tam = new Stack<Integer>();

		long ini = System.nanoTime();

		for(int i = 0; i < g.getNumVertices(); i++) {
			if(!visited[i]) {
				tam.push(IDFS(g, i));
			}
		}
		
		long fim = System.nanoTime();

		int n = 1;
		int maior = 0;
		int maiorPos = 0;

		while(!tam.empty()) {
			int t = tam.pop();

			if(t > maior) {
				maior = t;
				maiorPos = n;
			}

			n++;
		}
		System.out.println("O maior tamanho foi na " + maiorPos + "º busca e tem " + maior + " vertices");
		System.out.println("Tempo em segundos: " + ((fim - ini) * Math.pow(10, -9)));

	}

	public static AdjacencyList constructGraph(String path) {
		AdjacencyList g;

		try {
			BufferedReader buffer = new BufferedReader(new FileReader(path));

			String line = "";
			String node = "";
			int origNode = -1;
			int destNode = -1;
			int nodesNumber = Integer.parseInt(buffer.readLine());
			int edgeNumber = Integer.parseInt(buffer.readLine());
			int edge = 0;
			int lin = 2;

			g = new AdjacencyList(nodesNumber);

			while((line = buffer.readLine()) != null) {
				lin++;

				if(lin % 1000000 == 0)
					System.out.println("linha " + lin);
				
				node = "";
				if(line.charAt(0) == '#')
					continue;
				else {
					for(int i = 0; i < line.length(); i++) {
						if((line.charAt(i) == ' ' || line.charAt(i) == '	') && node != "") {
							origNode = Integer.parseInt(node);
							node = "";

						} else if((line.charAt(i) == ' ' || line.charAt(i) == '	')) {
							node = "";
							continue;

						} else {
							if(node == "")
								node = "";
							
							node = node + line.charAt(i);
						}
					}

					destNode = Integer.parseInt(node);

					g.addEdge(origNode, destNode);
					edge++;
				}

			}

			if(edgeNumber != edge) {
				System.out.println("Numero de arestas incompativel!");
				System.exit(0);
			}

			buffer.close();
			System.out.println("Numero de vertices: " + g.getNumVertices() + " Numero de arestas: " + edgeNumber);
			return g;
		} catch (IOException e) {
			System.out.println("arquivo não encontrado!");
		}

		return g = new AdjacencyList(0);
	}

	public static int IDFS(AdjacencyList G, int vertex) {
		int tam = 0;
		stack = new Stack<Integer>();
		stack.push(vertex);
		int v = 0;

		while(!stack.empty()){
			v = stack.pop();

			//Se vertice nao foi visitado antes, o marca como visitado e percorre
			//seus filhos os adicionando na pilha
			if(!visited[v]) {
				visited[v] = true;
				tam++;
				//System.out.println(v);
				Set<Integer> outNeighbours = G.getOutEdges(v);

				if(outNeighbours != null) {
					for(int elem : outNeighbours) {
						stack.push(elem);
					}
				}
			}
		}

		return tam;
	}
}