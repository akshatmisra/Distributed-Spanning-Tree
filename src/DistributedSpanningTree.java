import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;


public class DistributedSpanningTree {

	public static final int MESSAGE_SIZE = 1000;

	private static enum MessageTypes{EXPLORE, ACK, NACK};
	private static int rootNodeId = 0;
	private static int currNodeId;
	private static String currHostName;
	private static int currPort;
	private static Map<Integer, List<Integer>> nodesToChildrenMap = null;
	private static Map<Integer, String> nodeMap = null;
	private static Map<Integer, String> currNeighborsMap = null;
	private static Vector<String> exploredVec = null;
	private static Vector<String> recievedResponseVec = null;
	private static Set<Integer> nodesExplored = null;


	public static void main(String[] args)
	{

		exploredVec = new Vector<String>();
		recievedResponseVec = new Vector<String>();
		nodesToChildrenMap = new HashMap<Integer, List<Integer>>();
		nodesExplored = new HashSet<Integer>();

		currNodeId = Integer.parseInt(args[0].trim());
		currHostName = args[1].trim();
		currPort = Integer.parseInt(args[2].trim());
		rootNodeId = Integer.parseInt(args[3].trim());
		String [] neighbors = args[4].split(" ");
		String [] nodes_details = args[5].split("#");


		nodeMap = new HashMap<Integer, String>();
		for(int i=0; i < nodes_details.length; i++){
			String [] node_attr = nodes_details[i].split(" ");
			int nId = Integer.parseInt(node_attr[0].trim());
			nodeMap.put(nId, node_attr[1]+","+Integer.parseInt(node_attr[2].trim())+","+-1);
			List<Integer> children = new ArrayList<Integer>();
			nodesToChildrenMap.put(nId, children);
		}
		currNeighborsMap = new HashMap<Integer, String>();

		for(int j=0; j< neighbors.length; j++)
		{
			int key = Integer.parseInt(neighbors[j].trim());
			if(nodeMap.containsKey(key))
				currNeighborsMap.put(key, nodeMap.get(key));
		}

		PrintWriter writer;
		try 
		{
			writer = new PrintWriter(new FileOutputStream(new File("config-"+currNodeId+".txt"),true));
		} catch (Exception e) 
		{
			e.printStackTrace();
		}


		Thread t1 = new Thread()
		{
			public void run()
			{
				startServer();
			}
		};

		t1.start();

		boolean firstExplore = true;
		if(currNodeId == rootNodeId && firstExplore)
		{

			for(int neighbor : currNeighborsMap.keySet())
			{
				client(MessageTypes.EXPLORE, neighbor);
				synchronized((Object)exploredVec){
					nodesExplored.add(neighbor);
					exploredVec.add(currNodeId+","+neighbor);
				}
			}

			firstExplore = false;
		}
	}

	public static void startServer()
	{
		ServerSocket socket = null;

		try {
			socket = new ServerSocket(currPort);
		} catch (IOException e1) {
			try {
				Thread.sleep(30);
			} catch (InterruptedException e) {

			}
			startServer();
		}

		while(true)
		{
			String message = null;
			try
			{
				Socket soc = socket.accept();
				ObjectInputStream istream = new ObjectInputStream(soc.getInputStream());

				try {
					message = (String)istream.readObject();
				} catch (ClassNotFoundException e2) {
					// TODO Auto-generated catch block
					//e2.printStackTrace();
				}

				if(message != null && !message.isEmpty())
				{
					String msg[] = message.split(",");
					MessageTypes msgType = MessageTypes.valueOf(msg[0].trim());

					int srcNodeId = Integer.parseInt(msg[1].trim());
					int destNodeId = Integer.parseInt(msg[2].trim());

					String [] destNodeDetails = nodeMap.get(destNodeId).split(",");

					switch(msgType)
					{
					case EXPLORE:
						if(Integer.parseInt(destNodeDetails[2].trim()) == -1)
						{
							client(MessageTypes.ACK, srcNodeId);
							nodeMap.remove(destNodeId);
							nodeMap.put(destNodeId, destNodeDetails[0]+","+destNodeDetails[1]+","+srcNodeId);
						}
						else
						{
							client(MessageTypes.NACK, srcNodeId);
						}
						int destParent = Integer.parseInt(nodeMap.get(destNodeId).split(",")[2].trim());

						for(int neighbor : currNeighborsMap.keySet())
						{

							if(neighbor!= rootNodeId && !nodesExplored.contains(neighbor) && neighbor != destParent)
							{
								client(MessageTypes.EXPLORE, neighbor);
								synchronized((Object)exploredVec){
									nodesExplored.add(neighbor);
									exploredVec.add(currNodeId+","+neighbor);
								}
							}
						}

						break;

					case ACK:

						nodesToChildrenMap.get(destNodeId).add(srcNodeId);
						synchronized ((Object)recievedResponseVec) 
						{
							recievedResponseVec.add(destNodeId+","+srcNodeId);
						}
						break;

					case NACK:

						synchronized ((Object)recievedResponseVec) 
						{
							recievedResponseVec.add(destNodeId+","+srcNodeId);
						}
						break;

					default:
						break;
					}
				}

				synchronized (new Object()) 
				{
					if(exploredVec.size() == recievedResponseVec.size())
					{
						File f = new File("config-"+currNodeId+".txt");
						if(f.exists()){
							f.delete();
						}
						PrintWriter writer; 
						try 
						{
							writer = new PrintWriter(new FileOutputStream(new File("config-"+currNodeId+".txt"),true));
							int parentID =Integer.parseInt(nodeMap.get(currNodeId).split(",")[2].trim());
							writer.append(parentID == -1 ? "*\n" : parentID+"\n");
							String children ="";
							for(int n: nodesToChildrenMap.get(currNodeId))
							{
								children += n +"\t";
							}
							writer.append(children.isEmpty() ? "*\n" : children);				
							writer.close();
						} catch (Exception e)
						{
							//e.printStackTrace();
						}
					}
				}

			}catch(IOException e){
				//e.printStackTrace();
			}
		}	
	}


	public static String byteToString(ByteBuffer byteBuffer)
	{
		byteBuffer.position(0);
		byteBuffer.limit(MESSAGE_SIZE);
		byte[] bufArr = new byte[byteBuffer.remaining()];
		byteBuffer.get(bufArr);
		return new String(bufArr);
	}

	public static synchronized void client(MessageTypes msgType, int nId)
	{
		boolean allExplored = false;
		if(currNodeId == rootNodeId && nodesExplored.size() == currNeighborsMap.size()+1)
			allExplored = true;
		else if(currNodeId != rootNodeId && nodesExplored.size() == currNeighborsMap.size())
			allExplored = true;

		String [] destNodeDetails = nodeMap.get(nId).split(",");
		if(!allExplored)
		{
			try
			{ 
				String message = msgType.toString()+","+currNodeId+","+nId;
				Socket socket = new Socket(destNodeDetails[0]+".utdallas.edu",Integer.parseInt(destNodeDetails[1]));
				ObjectOutputStream ostream = new ObjectOutputStream(socket.getOutputStream());
				ostream.writeObject(message);
				ostream.flush();
			}
			catch(ConnectException ce){
				//throw ce;
				try {
					Thread.sleep(30);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				client(msgType,nId);
			}
			catch(IOException ioex)
			{
				//ioex.printStackTrace();
				try {
					Thread.sleep(30);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				client(msgType,nId);
			}
		}
	}
}