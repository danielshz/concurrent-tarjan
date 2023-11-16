import java.util.Random;
public class RandomGraph {

	private static Random r;

	public static void main(String[] args) {
		r = new Random();
		int vertexNumber = 0;
		double p = 0;

		//trata argumentos vindos do prompt
		if(args.length != 2){
			System.out.println("Falta argumentos! Digite <numero de vertices> <probabilidade p>");
			System.exit(0);
		}
		else if(Integer.parseInt(args[0]) < 1 || Integer.parseInt(args[1]) < 0){
			System.out.println("Argumentos invalidos!");
			System.exit(0);
		}
		else{
			vertexNumber = Integer.parseInt(args[0]);
			p = Double.parseDouble(args[1]) / 10000;
			if(p < 0 || p > 1){
				System.out.println("p invalido! intervalo aceito: p em [0, 100]");
				System.exit(0);
			}
		}

		generateRandomGraph(vertexNumber, p);
		
	}

	public static void generateRandomGraph(int v, double p){
		long edgeNumber = 0;

		//testa cada par de vertices do grafo
		//caso bernoulli retorne verdadeiro, então
		//cria uma aresta
		for(int i = 0; i < v; i++){
			for(int j = 0; j < v; j++){
				if(BernoulliDistribution(p)){
					System.out.println(i + "	" + j);
					edgeNumber++;
				}

			}
		}
		System.out.println(v);
		System.out.println(edgeNumber);
	}

	//gera um número aleatório entre 0 e 1 (ou quase isso). Caso seja menor
	//ou igual à p, retorna true
	public static Boolean BernoulliDistribution(double p){
		double prob = r.nextGaussian()*.33;
		return Math.abs(prob) <= p;
	}
}