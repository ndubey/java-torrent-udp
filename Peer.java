import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.net.*;
import java.io.*;
import java.util.*;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

public class Peer {
	public static final int CPORT = 2363;
	public static final int SPORT = 2364;
	public static final String magic = "1234";
	public static String newUserStr =  "0";
	public static String newUserRepStr =      "1";
	public static String beaconStr =          "2";
	public static String beaconRepStr =       "3";
	public static String updateStr =          "4";
	public static String updateboth =         "5";
	public static String updateUsers =        "6";
	public static String updateMain =         "7";
	public static String allreadyUpdated =    "8";
	public static String sendUserStr =        "9";
	public static String sendMainStr =        "10";
	public static String fileReqStr =         "11";
	public static String YfileReqRepStr =     "12";
	public static String NfileReqRepStr =     "13";
	public static String chunkDowStr =        "14";
	public static String fileUploadStr =      "15";
	public static String newfileUploadStr =   "16";
	public static String newfileUploadRepStr= "17";
	public static String fileUploadRepStr =   "19";
	public static String fileComDeleteStr =   "20";
	public static String fileComDeleteRepStr= "21";
	public static String fileDeleteStr =      "22";
	public static String fileDeleteRepStr =   "23";
	public static String fileDownloadedAck =  "24";
	public static String sendMasterStr  =    "28";
	public static void main(String[] args) throws Exception {
		System.out.println("\n\n\n************ Welcome to Group-2 P2P File Sharing Application ***************\n\n\n");
		Client client = new Client();
		Server server = new Server();
		client.start();
		server.start();
	}
}

class Client extends Thread {
	private static String hdr;
	private static DatagramPacket pack;
	private static DatagramSocket sock;
	private static String reply;
	private static byte[] smallMsg = new byte[1024];
	private static byte[] avgMsg = new byte[1024];
	private static DatagramPacket smallPack = new DatagramPacket(smallMsg, smallMsg.length);
	private static DatagramPacket avgPack = new DatagramPacket(avgMsg, avgMsg.length);
	private boolean newPeer = false;
	boolean  updatedUserlist = false;
	boolean updatedMain = false;
	boolean updatedMasterChunk = false;
	File main = new File( "main.txt" );
	File users = new File( "users.txt" );
	File temp = new File("temp.txt");
	File master = new File("masterChunk.txt");
	static Cubbyhole cb1;
	public Client(){
	}
	public void run() {
		try{
			sock = new DatagramSocket(Peer.CPORT);
		}
		catch (SocketException se){System.err.println(se);}
		cb1 = new Cubbyhole();
		Broadcast broadcast = new Broadcast(cb1, sock);
		broadcast.start();
		if(!users.exists()){
			newPeer = true;
			String onlineUserId = ShellUtils.getStringFromShell("**Please enter an online user ip ");
			System.out.println("Starting Sending Requests...");
			//System.out.println("Downloading user.txt and main.txt: \n");
			updatedUserlist = downloadFile(sock, "users.txt",onlineUserId);
			updatedMain = downloadFile(sock, "main.txt",onlineUserId);
			updatedMasterChunk = downloadFile(sock, "masterChunk.txt",onlineUserId);
			try{
				append(InetAddress.getLocalHost().getHostName().toString(),"users.txt");
			}catch(UnknownHostException uhe){}
			try{
				File file = new File("unfinishedDownload.txt");
				file.createNewFile();
				file = new File("shared.txt");
				file.createNewFile();
			}catch(IOException ioe){System.out.println("files can't be created inside new user");}
		}
		//System.out.println("************Checking who is online :**************");
		try{
			String myid = InetAddress.getLocalHost().getHostName();
			System.out.println("I am "+myid);
		}catch(UnknownHostException uhe){}
		if(!newPeer)
			hdr =Peer.magic +" "+Peer.beaconStr+" ";
		else//new peer
			hdr = Peer.magic +" "+Peer.newUserStr+" ";
			DatagramPacket beacon = new DatagramPacket(smallMsg, smallMsg.length);
			beacon.setData(hdr.getBytes());
		//first copy all user in temp.txt
		try{
			copy("users.txt","temp.txt");
		}catch(IOException ioe){}
		long time=System.currentTimeMillis();
		long timeout = 2000;
		pack = new DatagramPacket(smallMsg, smallMsg.length);
		int onlinecount=1;
		cb1.put(beacon);
		// broadcast thread broadcast from temp after every 100ms so it will wait for 500ms
		System.out.println("The following users are online:");
		while((System.currentTimeMillis()-time)<timeout){
			try{
				sock.setSoTimeout(500);//minimize it
				sock.receive(pack);
				reply = new String(pack.getData());
				if(reply.substring(0,4).equals(Peer.magic)){
					if((reply.substring(5,6).equals(Peer.beaconRepStr))||(reply.substring(5,6).equals(Peer.newUserRepStr))){
						System.out.println((onlinecount)+". "+(pack.getAddress().getHostName()));
						onlinecount++;
						deleteLine("temp.txt", pack.getAddress().getHostName().toString());
					}
					else{}//not a beacon msg
					//System.out.println("Client:some wrong msg check for solution "+reply);
				}
				else{}
					//System.out.println("Client:Recieved non-magical reply "+reply);
			}catch(SocketTimeoutException ste){}
			catch(IOException ioe){}
		}
		//System.out.println("******checking who is online is done :)*******");
		if((!updatedUserlist)&&(!updatedMain)&&(!newPeer)){
			//System.out.println("updated main.txt and user.txt\n\n");
			hdr = Peer.magic +" "+Peer.updateStr+" "+ users.lastModified() + " " +main.lastModified();//+ " " +master.lastModified();
			DatagramPacket updateReq = new DatagramPacket(smallMsg, smallMsg.length);
			updateReq.setData(hdr.getBytes());
			String line;
			try{
				FileReader userslist = new FileReader(users);
				BufferedReader br = new BufferedReader(userslist);
				while((line = br.readLine()) != null){
					if(searchLine("temp.txt", line)){}//do nothing read next line
					else{
						if((updatedUserlist)&&(updatedMain))
							break;//updated
						try{
							updateReq.setAddress(InetAddress.getByName(line));
						}catch(UnknownHostException uhe){}
						updateReq.setPort(Peer.SPORT);
						sock.send(updateReq);
						//System.out.println("Sent update request\n\n");
						try {
							sock.setSoTimeout(1000);//minimize it
							//System.out.println("waiting for reply");
							sock.receive(avgPack); 
							reply = new String(avgPack.getData());
							if(reply.substring(0,4).equals(Peer.magic)){//valid msg
								if(reply.substring(5,6).equals(Peer.allreadyUpdated)){
									//System.out.println("users.txt & main.txt are allready updated\n");
									updatedUserlist =true;
									updatedMain = true;
									//updatedMasterChunk = downloadFile(sock, "masterChunk.txt",line);
								}
								else if(reply.substring(5,6).equals(Peer.updateUsers)){
									//System.out.println("Only users.txt needs to be updated\n");
									updatedMain = true;
									updatedUserlist = downloadFile(sock, "users.txt",line);
								}
								else if(reply.substring(5,6).equals(Peer.updateMain)){
									//System.out.println("only main.txt and master.txt needs update\n");
									updatedUserlist = true;
									updatedMain = downloadFile(sock, "main.txt",line);
									updatedMasterChunk = downloadFile(sock, "masterChunk.txt",line);
								}
								else if(reply.substring(5,6).equals(Peer.updateboth)){
									//System.out.println("Your both files needs update\n");
									updatedUserlist = downloadFile( sock, "users.txt",line);
									updatedMain = downloadFile(sock, "main.txt",line);
									updatedMasterChunk = downloadFile(sock, "masterChunk.txt",line);
								}
							}
						}//end try
						catch (SocketTimeoutException ste){
							System.out.println ("Timeout Occurred");
						}
					}//end else
				}//end while
			}catch(FileNotFoundException fnf){}
			catch(IOException ioe){}
		}
		//System.out.println("Total number of onlineuser : "+onlinecount);
		//if((updatedUserlist)&&(updatedMain))
			System.out.println("\nSuccessfully connected to the group");
		/*else
			System.out.println("Not able to update both files due to some reasons");*/
		while(true){
			int options = ShellUtils.getIntFromShell("\nPlease enter your options:\n \t1 for Downloading a file from updated file list\n \t2 for uploading/sharing a file\n \t3 for deleting a file so removing that file from your shared list\n \t4 download unfinished files \n \t5 To unjoin the group.\nEnter your choice -> ");
			if(options == 1){
				String filename = ShellUtils.getStringFromShell("Please enter the filename/part of filename to start download: ");
				String result;
				result = grep("main.txt", filename);
				//assuming user can do any bad thing
				// so check again in main whether file is shared or not check in main.txt
				if(result.equals("-1")){//user mistakenly entered 0
				}
				else{//download file
					// chk whether user has entered correctly
					System.out.println(" starting download of "+result+"...");
					if(search("main.txt",result)){
						filename = result;
						File f = new File(filename);
						if(f.exists()){
							String answer = ShellUtils.getStringFromShell("File with filename "+filename+" all ready exists.\nWant to overwrite ?(yes/no): ");
							while(!(answer.equals("yes")||answer.equals("no")))
								answer = ShellUtils.getStringFromShell("Please enter yes/no.->");
							if(answer.equals("no")){
								continue;
							}
						}

						String finl = Peer.magic + " "+Peer.fileReqStr+" "+filename;
						smallPack.setData(finl.getBytes());
						try{
						copy("users.txt","temp.txt");
						}catch(IOException ioe){}
						time=System.currentTimeMillis();
						timeout = 5000;
						//String tempFile = filename+"_temp.txt";
						boolean now = false;
						//System.out.println("brdcsting");
						cb1.put(smallPack);//done broadcst 
						//now listen
						int filecount = 0;
						String array[] = new String[105];
						while((System.currentTimeMillis()-time)<timeout){//if user is not putting anything else to download try downloading these if they are not complete yet
							try{
							byte[] newbuf = new byte[1024];
							DatagramPacket pack1 = new DatagramPacket(newbuf,newbuf.length);
							sock.receive(pack1); //they will send magic filename filesize totalchunks chunks_I_have
							reply = new String(pack1.getData());
							//System.out.println("Client:got a packet from "+pack.getAddress().getHostName()+" Saying "+reply);

							int i = 0;
							StringTokenizer st = new StringTokenizer(reply);
							while (st.hasMoreTokens()) {
								array[i] = st.nextToken();
								i++;
							}
							if(array[0].equals(Peer.magic)){
    								//String filesize = array[3];
								String totalchunks = array[3];
								if(array[1].equals(Peer.YfileReqRepStr)){
									System.out.println(pack1.getAddress().getHostName()+" user has the file "+filename);
									if(filecount == 0){//first reply construct basic file
										//append filename filesize total number of chunks count of chunks at top
										    try {
										        File file = new File(filename+"_temp.txt");
										       	if(file.exists())
										       		file.delete();
    											file.createNewFile();

											//append it to unfinished download
											//do construction from there only
											String appendStr=filename + " " + totalchunks;
											//System.out.println("Client: Appending "+appendStr+" to "+filename+"_temp.txt");
											int Totalchunks = Integer.valueOf( totalchunks ).intValue();
											for(int k=0; k< Totalchunks;k++)
												appendStr = appendStr + " " +k;
											append(appendStr,"unfinishedDownload.txt");
											//append(appendStr,filename+"_temp.txt");
											filecount++;
											}
										    catch (IOException e) {
											System.out.println("could not create "+filename+"_temp.txt");
										    }
										}
										
										filecount++;
										deleteLine("temp.txt", pack1.getAddress().getHostName().toString() );
										reply = pack1.getAddress().getHostName() + " " + reply.substring(8,reply.length());
										now = true;
										reply=reply.trim();
										//System.out.println("appending to _temp.txt "+reply);
										append(reply,filename+"_temp.txt");
										//pw.println(reply);//"Adding a line..."
										//creating lists in file so that it can be used finally to download
										//or else append(reply,filename+"_temp.txt");
									
								}
								else{//he does not have file
									deleteLine("temp.txt", pack1.getAddress().getHostName().toString());
									//System.out.println(pack.getAddress().getHostName()+" has replied he has not the file saying "+reply);
								}
							}
							else{}
								//System.out.println("Recieve non magical reply from"+reply);
   						}catch(SocketTimeoutException ste){}
						catch(IOException ioe){}
						}//end creating filelist
					//downloadFile(sock,filename,"csews6.cse.iitk.ac.in");
						if(now)//temporary file list has been created download using that file
							downloadFrom(filename,Integer.parseInt(array[3]));//,Integer.parseInt(array[3]));
						else{//no user with that file is online currently so try latter
							//append((filename + " 0"),"unfinishedDownload.txt");
							System.out.println("No user with this file is online currently. \nAppended in unfinishedDownload.txt");
						}

					}//end if
				}
			}//end if(options) 
			
			else if(options==2){//uploading file
				String filename = ShellUtils.getStringFromShell("Please enter the file name  to upload: ");
				int j=0;
				filename = filename.trim();
				File f = new File(filename);
				if(!f.exists()){
					filename = ShellUtils.getStringFromShell("This file does not exist.\nPlease re-enter the file name: ");
					f = new File(filename);
					if(!f.exists()){
						System.out.println("Even this file does not exist");
					continue;
					}
				}
				filename = filename + " " + filename;
				while(filename.charAt(j) != ' ')j++;
				boolean AllreadyThr = search("shared.txt",filename.substring(0,j));
				if(AllreadyThr)
					System.out.println("OOPS!! This file is already shared by you");
				else{
					appendSharedFile(filename, "shared.txt");//first get file size and number of chunks increment it in filename then write it to shared.txt
					AllreadyThr = search("main.txt", filename.substring(0,j));
					if(AllreadyThr){
						String Line = CmpltLine("main.txt",filename.substring(0,j));
						incrementCount("main.txt",filename.substring(0,j));
						String increment=Peer.magic + " "+Peer.fileUploadStr+" "+Line;//00 is used for file request
						smallPack.setData(increment.getBytes());
						try{
						copy("users.txt","temp.txt");
						time=System.currentTimeMillis();
						cb1.put(smallPack);//done broadcst
						while((System.currentTimeMillis()-time)<timeout){
							sock.receive(pack); 
							reply = new String(pack.getData());
							if(reply.substring(0,4).equals(Peer.magic)){
								if(reply.substring(5,7).equals(Peer.fileUploadRepStr)){
									deleteLine("temp.txt", pack.getAddress().getHostName().toString() );
								}
								else{}//not a beacon msg 
									//System.out.println("some wrong msg check for solution"+reply);
							}
								//System.out.println("Recieve msg fr om some other type not from our sources"+reply);
						}
						}catch(IOException ioe){}
						//catch(SocketTimeoutException ste){}
					}
					else{//it's a new share
						append((filename.substring(0,j)+" 1"),"main.txt");
						makeChunks(filename.substring(0,j));
						//again broad cast it to all so that the can also append it
						String insert = Peer.magic + " "+Peer.newfileUploadStr+" "+filename;//00 is used for file request
						smallPack.setData(insert.getBytes());
						try{
						copy("users.txt","temp.txt"); //?????????????
						time=System.currentTimeMillis();
						cb1.put(smallPack);//done broadcst
						while((System.currentTimeMillis()-time)<timeout){
							sock.receive(pack); 
							reply = new String(pack.getData());
							if(reply.substring(0,4).equals(Peer.magic)){
								if(reply.substring(5,7).equals(Peer.newfileUploadRepStr)){
									//System.out.println(pack.getAddress().getHostName()+" user has inserted this new file shared by you in his main.txt \n");
									deleteLine("temp.txt", pack.getAddress().getHostName().toString());
									sendmasterChunk(sock,pack.getAddress());
								}
							else{}
								//System.out.println("some wrong msg check for solution"+reply);
							}
								//System.out.println("Recieve msg fr om some other type not from our sources"+reply);
						}
						}catch(IOException ioe){}
						//catch(SocketTimeoutException ste){}
					}

				}//end else
				System.out.println("The file is succesfully uploaded");
			}//end options 

			else if(options==3){//deleting a file from your sharing
				String filename = ShellUtils.getStringFromShell("Please enter the filename or part of filename which you want to delete ");
				String result;// = new String[2];
				result=grep("shared.txt",filename);
				// chk it in shared .txt if not there print it 
				//else delete that entry and send again a packet to all user about informing this

				if(result.equals("-1")){//users do not want to delete these files
				}
				else {//delete that file
					String Filename = result;
					//String lineWithFilename = RetLineSearch("shared.txt",Filename);
					//System.out.println(" filename "+result);
					//search in shared.txt for its entry
					boolean isThr = search( "shared.txt",Filename);
					if(isThr){
						deleteSearchLine("shared.txt",Filename);
						//while(lineWithFilename.charAt(i)!=' ')i++;
						String Line = CmpltLine("main.txt",Filename);//returns the line with this filename	
						//System.out.println("Line in main is "+Line);
						decrementCount(Filename,"main.txt");
						//if the count become 0 delete that entry completely
						//broadcst it to all
						String finl=Peer.magic + " "+Peer.fileDeleteStr+" "+Line;//00 is used for file request
						smallPack.setData(finl.getBytes());
						try{
						copy("users.txt","temp.txt");
						}catch(IOException ioe){}
						time=System.currentTimeMillis();
						timeout = 5000;
						cb1.put(smallPack);//done broadcst 
						//now listen

						while((System.currentTimeMillis()-time)<timeout){
						try{
							sock.receive(pack); 
						reply = new String(pack.getData());
						if(reply.substring(0,4).equals(Peer.magic)){
	 						if(reply.substring(5,7).equals(Peer.fileDeleteRepStr)){
								//System.out.println(pack.getAddress().getHostName()+" user has decremented in his main.txt");
								deleteLine("temp.txt", pack.getAddress().getHostName().toString());
								}
								else{}//not a beacon msg 
									//System.out.println("some wrong msg check for solution"+reply);
							}

						}catch(IOException ioe){}
  						}

					}
					else
						System.out.println("sorry the file is not shared by you so you can't remove it from sharing it \n");
				}
			}//end option 

	    else if(options==4){
		try{
		    File inputFile = new File("unfinishedDownload.txt");
		    FileReader in = new FileReader(inputFile);
		    BufferedReader br = new BufferedReader(in);
		    String  c = "";
		    c=br.readLine();
		   while(c != null && (!c.equals(""))){
		    int i=0;
		    while(c.charAt(i)!=' ')i++;
		    String filename = c.substring(0,i);
		    String finl=Peer.magic + " "+Peer.fileReqStr+" "+filename;
		    smallPack.setData(finl.getBytes());
		    copy("users.txt","temp.txt");
		    time=System.currentTimeMillis();
		    timeout = 5000;//more or less check
		    boolean now = false;
		    cb1.put(smallPack);//done broadcst 
		    //now listen
		    int filecount = 0;
		    //String array[] = new String[4];
		    String totalchunks = "-1";
		    while((System.currentTimeMillis()-time)<timeout){
		    	try{
		    	byte[] newbuf = new byte[1024];
				DatagramPacket pack1 = new DatagramPacket(newbuf,newbuf.length);
			    sock.receive(pack1); //they will send magic filename filesize totalchunks chunks_I_have
			    reply = new String(pack1.getData());
			    //System.out.println("Client:got a packet from "+pack.getAddress().getHostName()+" Saying "+reply);
			    i = 0;
			    StringTokenizer st = new StringTokenizer(reply);
			    while (i<3) {
				st.nextToken();
				i++;
			    }
			    totalchunks = st.nextToken();
			    String array2[] = new String[Integer.parseInt(totalchunks)+5];
			    i=0;
			    st = new StringTokenizer(reply);
			    while (st.hasMoreTokens()) {
				array2[i] = st.nextToken();
				i++;
			    }
			    if(array2[0].equals(Peer.magic)){
				if(array2[2].equals(filename)){
				    System.out.println(pack1.getAddress().getHostName()+" user has the file "+filename);
				    if(filecount == 0){//first reply construct basic file
					//append filename filesize total number of chunks count of chunks at top
					try {
					    File file = new File(filename+"_temp.txt");
					    file.createNewFile();
					}
					catch (IOException e) {
					    System.out.println("could not create "+filename+"_temp.txt");
					}
				    }
				    filecount++;
				    deleteLine("temp.txt", pack1.getAddress().getHostName().toString() );
				    reply = pack1.getAddress().getHostName() + " " + reply.substring(8,reply.length());
				    now = true;
				    reply=reply.trim();
				    append(reply,filename+"_temp.txt");
				}
				else{//he does not have file
				    deleteLine("temp.txt", pack1.getAddress().getHostName().toString());
				    //System.out.println(pack.getAddress().getHostName()+" has replied he has not the file saying "+reply);
				}
			    }
			    
			}catch(SocketTimeoutException ste){}
			catch(IOException ioe){}
		    }//end while
		    if(now){
			if(totalchunks.equals("-1")){
			    System.out.println("Got an invalid data");
			    continue;
			}
			else{
			    downloadFrom(filename,Integer.parseInt(totalchunks));
			}
		    }
		    else{//no user with that file is online currently so try latter
			System.out.println("Again no user with this file is online. Try later");
		    }
		    if((c = br.readLine())!= null){
			String answer = ShellUtils.getStringFromShell("Wants to download another unfinished file?(yes/no) ->");
			if(answer.equals("no"))
			    break;
		    }
		}//end while(c != null)
		}//end try
		catch (Exception e){
		    System.out.println("Exception in option 4 "+e);
		}
	    }//ends option 4

			else if(options==5){
				System.out.println("Thanks for using the application.");
				File fileToDel = new File("users.txt");
				fileToDel.delete();
				fileToDel = new File("main.txt");
				fileToDel.delete();
				fileToDel = new File("masterChunks.txt");
				fileToDel.delete();
				fileToDel = new File("unfinishedDownload.txt");
				fileToDel.delete();
				fileToDel = new File("temp.txt");
				fileToDel.delete();
				fileToDel = new File("shared.txt");
				fileToDel.delete();
				System.exit(0);
			}//ends option 5
		}//end true loop
	}//end run
//methods used by client


        public static boolean downloadFile(DatagramSocket sock, String filename , String address){
                //first send download request   
                int count =0;//send file download request 4 times
                boolean notStarted = true, flag = false;
                String hdr;
                while((count < 4)&&(notStarted)){
                        count++;
                        try{
                                InetAddress addr = InetAddress.getByName(address);
                                if(filename.equals("users.txt"))
                                        hdr = Peer.magic +" "+Peer.sendUserStr;
                                else  if(filename.equals("main.txt"))
                                        hdr = Peer.magic + " "+Peer.sendMainStr;
				else
                                        hdr = Peer.magic + " "+Peer.sendMasterStr;
                                byte[] buf = new byte[1600];
                                
                                buf = hdr.getBytes();
                                DatagramPacket packet = new DatagramPacket(buf, buf.length, addr, Peer.SPORT);
                                sock.send(packet);
                                File fp1 = new File(filename);
                                FileOutputStream fp = new FileOutputStream(filename);      
                              //size of file
                                //System.out.println("file request is sent everything ok now downloading the file "+filename);
                                while(true){
                                        sock.setSoTimeout(1000);
                                        try {//you may also check whether it is correct file packet or something else
                                        		byte[] receiveData = new byte[1600];
                                        		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);  // 5. create datagram packet for incoming         datagram
                                                sock.receive(receivePacket);  
                                        //String modifiedSentence = new String(receivePacket.getData()); // 7. retrieve the data from buffer
                                                InetAddress returnIPAddress =  receivePacket.getAddress();  // get ipddress
                                                int port = receivePacket.getPort();  // get port number
                                                //System.out.println ("From server at: " + returnIPAddress +  ":" + port);
                                        //System.out.println("Message: " + modifiedSentence);
						String d = new String(receivePacket.getData());
						d = d.trim();
                                                String see = new String(receivePacket.getData());
                                                see = see.trim();
                                                //System.out.println("inside the packet"+see); 
                                                //System.out.println(" downloading file   \n");
                                                notStarted = false;
                                                String ackn="ack";
                                                byte[] buff = new byte[1600];
                                                buff = ackn.getBytes();
                                                DatagramPacket pkt = new DatagramPacket(buff, buff.length, returnIPAddress, port);
                                                sock.send(pkt);
                                                //System.out.println("ACK sent \n");
						if(see.charAt(0)=='@' && see.charAt(1)=='#' && see.charAt(2)=='$'){  //last packet	       
         		        			flag =true;
         						break;
         					}
						else
							 fp.write(d.getBytes());
                                        }//end try
                                catch (SocketTimeoutException ste)
                                {
                                        System.out.println ("Timeout Occurred: Packet assumed lost");}
                        }//end while 
                        fp.close();
                }//end try
                catch (UnknownHostException ex) { System.err.println(ex);}
                catch (IOException ex) {System.err.println(ex);}
                catch (Exception e){System.err.println("File input error");}

                }//end while
		if(flag){
				byte[] buf2 = new byte[1600];
				buf2 = (Peer.magic +" "+Peer.fileDownloadedAck+" "+filename).getBytes();

				try{
				InetAddress addr = InetAddress.getByName(address);
				DatagramPacket ackpack = new DatagramPacket(buf2, buf2.length, addr, Peer.SPORT);
				sock.send(ackpack);
				} catch(Exception e){
					System.out.println(e);
				}
		}
                return flag;
        }//end method


public static void sendmasterChunk(DatagramSocket sock, InetAddress addr){
		//System.out.println("inside send file method for file ");
        	byte bytes[] = new byte[1600];
		try{
		File inputFile = new File("masterChunk.txt");
		InputStream in = new FileInputStream(inputFile);
		int io;
		while ((io = in.read(bytes, 0, 1600)) > 0){
			DatagramPacket packet;
			//System.out.println("hr1");
			if(io < 1600){
					//System.out.println("hr2");
					byte byte2[] = new byte[io];
					int j = 0;
					while(j < io){
						byte2[j] = bytes[j];j++;
					}
					packet = new DatagramPacket(byte2,io, addr,Peer.SPORT);
			}
			else
				packet = new DatagramPacket(bytes, bytes.length, addr,Peer.SPORT);
			boolean lost = true;
			byte[] receiveData = new byte[1600];
			int i=0;
			while(lost==true)
			{
				try{
					//System.out.print("Client:In sendfile. Now sending ");
					sock.send(packet);
				}
				catch (IOException ex){ System.err.println(ex);}
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				try {
					sock.setSoTimeout(5000);
					sock.receive(receivePacket);
					InetAddress returnIPAddress = receivePacket.getAddress();  // get ipddress
					//int port = receivePacket.getPort();  // get port number
					lost = false;
				}
				catch (SocketTimeoutException ste){
					System.out.println ("Timeout Occurred: Packet assumed lost");
					System.out.println ("Server: message "+" re-attempt no." + i);
					lost = true;
					i++;
					if(i==5){	
					return;
					}
				}//end catch
				catch (UnknownHostException ex) { System.err.println(ex);}
				catch (SocketException se) {
					System.err.println(se);
				}
				catch (IOException e) {
					System.err.println("Packet " + i + " failed with " + e);
				}
			}//end while
		}//end while
		bytes = "@#$".getBytes();
		DatagramPacket pac = new DatagramPacket(bytes, bytes.length, addr,Peer.SPORT);
		try{
			//System.out.print("Client:In sendfile. Now sending "+new String(bytes));
			sock.send(pac);
		}
		catch (IOException ex){ System.err.println(ex);}
		}catch(IOException ioe){}
	}//end method filerequest
		


//String Line = CmpltLine("main.txt",filename.substring(0,j));//returns the line with this filename	
	public static String CmpltLine(String filename, String part){
		try{
		File f = new File(filename);
		FileReader in = new FileReader(f);
		BufferedReader br = new BufferedReader(in);
		String  c;
		while ((c=br.readLine()) !=null)
		{
			if(c.indexOf(part) != -1)
				return c;
		}
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
		return "error";
	}


	public static String grep(String filename, String part){
		String reply= "-1";
		String[] d = new String[20];
		int temp;
		try{
		File f = new File(filename);
		FileReader in = new FileReader(f);
		BufferedReader br = new BufferedReader(in);
		// Continue to read lines while
		// there are still some left to read
		String  c;int count = 1; int choice = 0;
		while ((c=br.readLine()) !=null)
		{
			if(c.indexOf(part) != -1){
				StringTokenizer st = new StringTokenizer(c);
				d[count] = st.nextToken();
				System.out.println(count+" " +d[count]);
				count++;
			}
		}
		if(count == 0){
			reply = "-1";
			System.out.println("\t\tno match found try again\t\t");
			return "-1";
		}
		else{
			choice = ShellUtils.getIntFromShell("Please enter the file number to select the file or enter 0 to gack to main menu ->");
			reply.trim();
			if(choice<=count&&choice>0)
				reply = d[choice];
			else
				reply = "-1";//filename is saved
		}
		}catch(FileNotFoundException fnf){System.out.println("fille"+fnf);}
		catch( IOException ioe){System.out.println("error"+ioe);}
		return reply;
	}



		public void makeChunks(String newfilename){
                        int mb  = 1024*1024;
                        byte[] waste = new byte[mb];
                        byte[] waste2 = new byte[mb];
                        try{
                        FileInputStream fstream = new FileInputStream(newfilename);
                        DataInputStream in = new DataInputStream(fstream);
                        //int dummy = chunkIndex; 
                        String temp, strToAppend, sha;
                        int chunkNo = 0;
			int io = in.read(waste,0,mb);
                        while(io>0){//updated the piointer for getting the required chunk
                                temp = new String(waste);
                                if(io < mb){
                                        for(int i=0;i<io;i++)
                                                waste2[i] = waste[i];
                                        temp = new String(waste2);
                                }
                                sha = client_threads.SHA1(temp);
                                strToAppend = newfilename+" "+chunkNo+" "+sha;
                                append(strToAppend, "masterChunk.txt");
                                chunkNo++;
				io = in.read(waste,0,mb);
                        }
			//System.out.println("\t\tExiting makeChunk\t");
                        }
                        catch (Exception e ){System.out.println(e);}
        }
        


	
	public void deleteLine(String file, String lineToRemove) {
	//System.out.println("inside delete line method line to delete is : "+lineToRemove);
    	try {
			File inFile = new File(file);
			if (!inFile.isFile()) {
        		System.out.println("Parameter is not an existing file");
        		return;
      		}
      //Construct the new file that will later be renamed to the original filename.
      		File tempFile = new File(inFile.getAbsolutePath() + ".tmp");
      		BufferedReader br = new BufferedReader(new FileReader(file));
      		PrintWriter pw = new PrintWriter(new FileWriter(tempFile));
      		String line = null;
      		//Read from the original file and write to the new
      		//unless content matches data to be removed.
      		while ((line = br.readLine()) != null) { 
			//System.out.println("Current line is : "+line);//for debugging
       			if (!line.trim().equals(lineToRemove)) {
				//System.out.println("line has been copied so not deleted: ");//for debugging
          			pw.println(line);
          			pw.flush();
        		}
      		}
      		pw.close();
      		br.close();
      		//Delete the original file
      		if (!inFile.delete()) {
        		System.out.println("Could not delete file");
        		return;
      		}
      		//Rename the new file to the filename the original file had.
      		if (!tempFile.renameTo(inFile))
        		System.out.println("Could not rename file");
    	}
    	catch (FileNotFoundException ex) {
      		ex.printStackTrace();
    	}
    	catch (IOException ex) {
			ex.printStackTrace();
    	}
	} //ends method

	public static void copy(String fileSrc, String fileDst) throws IOException {
	//System.out.println("Copying file: src:"+fileSrc+" to dst:"+fileDst);
	try{
	File src = new File(fileSrc);
	File dst = new File(fileDst);
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
    
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0)
            out.write(buf, 0, len);
        in.close();
        out.close();
	}catch(FileNotFoundException fnfe){}
	
    }
 
	public static boolean searchLine(String file, String query){
		try{
    	// Open the file that is the first 
    	// command line parameter
    	FileInputStream fstream = new FileInputStream(file);
    	// Get the object of DataInputStream
    	DataInputStream in = new DataInputStream(fstream);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
    	String strLine;
    	//Read File Line By Line
    	while ((strLine = br.readLine()) != null)   {
      		// Print the content on the console
			if(strLine.equals(query)){
				in.close();
				return true;
			}
      		//System.out.print(strLine);
    	}
    	//Close the input stream
    	in.close();
    }catch (Exception e){//Catch exception if any
		System.err.println("Error: " + e.getMessage());}
	return false;
	}
	
	public static boolean search(String f, String toSearch){
		try{
		File file = new File(f);
		FileReader in = new FileReader(file);
		BufferedReader br = new BufferedReader(in);
		// Continue to read lines while
		// there are still some left to read
		String  c;
		while ((c=br.readLine()) !=null)
		{
			StringTokenizer st = new StringTokenizer(c);
			if(st.nextToken().equals(toSearch))
				return true;
		}
		}catch(FileNotFoundException fnf){System.out.println("fille"+fnf);}
		catch( IOException ioe){System.out.println("error"+ioe);}
		return false;
	}
	//append(appendStr,filename+"_temp.txt");
	public static void append(String toAppend, String filename){
		//System.out.println("inside append "+filename);
		try{
		File file = new File(filename);
		String last = getAppendedContents(file,toAppend);
		setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done appending

	public static String getAppendedContents(File aFile, String toAppend) {
    //...checks on aFile are elided
		StringBuilder contents = new StringBuilder();
    
		try {
		//use buffering, reading one line at a time
		//FileReader always assumes default encoding is OK!
		BufferedReader input =  new BufferedReader(new FileReader(aFile));
		try {
			//System.out.println("in try for stringto append "+toAppend+" for file "+aFile.getName());
			String line = null; //not declared within while loop
			while (( line = input.readLine()) != null){
				//System.out.println("inside appending this line " + line + "$");
				if(line.equals("")) continue;
				contents.append(line);
				contents.append(System.getProperty("line.separator"));
			}
			contents.append(toAppend);
			contents.append(System.getProperty("line.separator"));
		}
		finally {
			input.close();
		}
	}
	catch (IOException ex){
		ex.printStackTrace();
	}
    
	return contents.toString();
	}	
	
	public static void setContents(File aFile, String aContents)
                                 throws FileNotFoundException, IOException {
		if (aFile == null) {
			throw new IllegalArgumentException("File should not be null.");
		}
		if (!aFile.exists()) {
			throw new FileNotFoundException ("File does not exist: " + aFile);
		}
		if (!aFile.isFile()) {
			throw new IllegalArgumentException("Should not be a directory: " + aFile);
		}
		if (!aFile.canWrite()) {
			throw new IllegalArgumentException("File cannot be written: " + aFile);
		}

    //use buffering
		Writer output = new BufferedWriter(new FileWriter(aFile));
		try {
			//FileWriter always assumes default encoding is OK!
			output.write( aContents );
		}
		finally {
			output.close();
		}
	}

	
//incrementCount("main.txt",filename.substring(0,j));//increment count for this shared file
	//this method is also used in server b/c he also do these when receive such request.
	public static void incrementCount(String filename, String toIncrement){
		try{
		File file = new File(filename);
		String last = getIncrementedContents(file,toIncrement);
		//System.out.println("calling setcontents from increment count ");
		setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done incrementing

	public static String getIncrementedContents(File aFile, String toIncrement) {
    //...checks on aFile are elided
		StringBuilder contents = new StringBuilder();
		int initialCount, finalCount;
    
		try {
		//use buffering, reading one line at a time
		//FileReader always assumes default encoding is OK!
		BufferedReader input =  new BufferedReader(new FileReader(aFile));
		try {
			String line = null; //not declared within while loop
			while (( line = input.readLine()) != null){
				StringTokenizer st = new StringTokenizer(line);
				if(st.nextToken().equals(toIncrement)){
					   initialCount = Integer.valueOf( st.nextToken()).intValue();
					   finalCount = initialCount + 1;
					   line = toIncrement + " " + finalCount;
				}
				contents.append(line);
				contents.append(System.getProperty("line.separator"));
			}
		}
		finally {
			input.close();
		}
	}
	catch (IOException ex){
		ex.printStackTrace();
	}
    
	return contents.toString();
	}	
	
	public static void deleteSearchLine(String filename, String lineWithFilename){
		//System.out.println("in deletesrchline");
		try{
		File file = new File(filename);
		String last = getDeletedSearchLine(file,lineWithFilename);
		setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done decrementing

	public static String getDeletedSearchLine(File aFile, String lineWithFilename) {
    //...checks on aFile are elided
		StringBuilder contents = new StringBuilder();
		boolean toAdd = true;
    
		try {
		//use buffering, reading one line at a time
		//FileReader always assumes default encoding is OK!
		BufferedReader input =  new BufferedReader(new FileReader(aFile));
		try {
			String line = null; //not declared within while loop
			while (( line = input.readLine()) != null){
				StringTokenizer st = new StringTokenizer(line);
				toAdd = true;
				if(st.nextToken().equals(lineWithFilename)){
						toAdd = false;
						System.out.println("deleted "+lineWithFilename);
						//System.out.println("line in delete method is "+line);
				}
				if(toAdd){
					contents.append(line);
					contents.append(System.getProperty("line.separator"));
				}
			}
		}
		finally {
			input.close();
		}
	}
	catch (IOException ex){
		ex.printStackTrace();
	}

	return contents.toString();
	}	
		
		//	decrement(lineWithFilename.substring(0,i),"main.txt");
		//if the count become 0 delete that entry completely

	public static void decrementCount(String toDecrement, String filename){
		try{
		File file = new File(filename);
		String last = getDecrementedContents(file,toDecrement);
		//System.out.println("inside decrement count calling set contents ");
		setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done decrementing
	

	public static String getDecrementedContents(File aFile, String toDecrement) {
    //...checks on aFile are elided
		StringBuilder contents = new StringBuilder();
		int initialCount, finalCount;
		boolean toAdd = true;
    
		try {
		//use buffering, reading one line at a time
		//FileReader always assumes default encoding is OK!
		BufferedReader input =  new BufferedReader(new FileReader(aFile));
		try {
			String line = null; //not declared within while loop
			while (( line = input.readLine()) != null){
				StringTokenizer st = new StringTokenizer(line);
				toAdd = true;
				if(st.nextToken().equals(toDecrement)){
					initialCount = Integer.valueOf( st.nextToken()).intValue();
					finalCount = initialCount - 1;
					if(finalCount > 0)
						line = toDecrement + " " + finalCount;   
					else
						toAdd = false;
				}
				if(toAdd){
					contents.append(line);
					contents.append(System.getProperty("line.separator"));
				}
			}
		}
		finally {
			input.close();
		}
	}
	catch (IOException ex){
		ex.printStackTrace();
	}
    
	return contents.toString();
	}	
	
	
	
		//appendSharedFile(filename, "shared.txt");//this method will find the size of file append line in shared.txt in its format calculating chunks and all that here filename = filename location
		
	public static void appendSharedFile(String tfilename, String filename){
		String line="";
		long file_size = 0;
		int totalChunks = 0;
		StringTokenizer st = new StringTokenizer(tfilename);
		line = st.nextToken();
		String path = st.nextToken();
    	File f = new File(path);  
		if(f.exists()){
   
   			file_size = f.length();
   			totalChunks = (int)(file_size/(1024*1024));
   			if(!(file_size %(1024*1024)==0))
   			totalChunks++;
   			line = line + " " + totalChunks;
   			int temp = 0; 
   			while(temp < totalChunks){
   				line = line + " " + temp;
   				temp++;
   			}
   			line = line + " " + path;
   			append(line, "shared.txt");
		}
	}
	
  
	//	downloadFrom(filename+"_temp.txt");//download from this file which contains information about where to get chunks
//this is to send request for chunks in least comon chunk first order

public static void downloadFrom(String filename, int totalChunks){
                Downloader downloader = new Downloader(filename,totalChunks);
                downloader.start();
        }


}//end client class


class Downloader extends Thread{

	public String[] user;
	public String  filename;
	//private String  tempfilename;
	public int num_users=0;
	public static int max_slots=4;
	public static int totalChunks;
	private int chunkRem;
	public static String magic = "1234";
	public static int chunkArray[];
	public static boolean [] timeout=new boolean[max_slots];	
	public static boolean [] slot_free=new boolean[max_slots];
	public static boolean [] under_process;
	public static boolean [] thisSession;
	public static boolean [] downloaded;
	public static boolean [] userBusy;
	public static boolean [] userDead;
	public static final int MAX_CHUNKS = 103;    // Max size which can be downloaded is 100MB. Change accordingly
	public static boolean allUserBusy = false;
	public static boolean allSlotsBusy = false;
	public static client_threads [] clt=new client_threads[max_slots];
	public static long TIMEOUT = 5000;
	//public static  int debug[];
	public static int deadCount[];
	public Downloader(String filename, int i){
		this.filename=filename;
		totalChunks=i;
	}
	public void run(){
		num_users = 0;
		int i = 0;
		int count = 0; //no. of chunks to downloaded in this session
		System.out.println("In class Downloader for file "+filename);
		//int chunkRem=intelligentParser(filename+"_temp.txt");  //chunkREm = total chunks in file
		File f = new File("unfinishedDownload.txt");
		StringTokenizer st;
		String c;
		try{
		FileReader in = new FileReader(f);
		BufferedReader br = new BufferedReader(in);
		c = br.readLine();
		st = new StringTokenizer(c);
		while(c !=null){
			//System.out.println("inside downloader while loop line in unfinished download is " + c);
			if(st.nextToken().equals(filename)){
				//System.out.println(" file name found break");
				break;
			}
			c = br.readLine();
			if(c != null)
			st = new StringTokenizer(c);
		}
		//System.out.println("line in unfinished is "+c);
		st.nextToken();
		//initializing
		//st.nextToken();
		while(st.hasMoreTokens()){
			count++;
			st.nextToken();
		}
		under_process = new boolean[count];
		//chunkBusy = new boolean[count];
		downloaded = new boolean[count];
		thisSession = new boolean[count];
		//debug = new int[count];
		chunkArray = new int[count];
		int rarest[] = new int[count]; //sorted array of chunks
		while(i<count){
			under_process[i] = false;
			thisSession[i] = true;
			downloaded[i] = false;
			under_process[i] = false;
			//debug[i] = 0;
			rarest[i] = 0;
			i++;
		}
		StringTokenizer st1 = new StringTokenizer(c);
		//System.out.println("line" +c);
		st1.nextToken();
		st1.nextToken();
		//System.out.println("The chunkarray has "); 
		i=0;
		while(st1.hasMoreTokens()){
			//System.out.println(" i is "+i);
			chunkArray[i] = Integer.parseInt(st1.nextToken());
			System.out.print(chunkArray[i]+" ");
			i++;
		}
		//System.out.println("out side");
		File fil = new File(filename+"_temp.txt");
		FileReader fr  = new FileReader(fil);
		BufferedReader bfd = new BufferedReader(fr);
		while((c = bfd.readLine())!=null){
			StringTokenizer sti = new StringTokenizer(c);
			if(sti.nextToken().equals(InetAddress.getLocalHost().getHostName()))
				continue;
			num_users++; // to count total users 
		}
		userBusy = new boolean[num_users];
		userDead = new boolean[num_users];
		deadCount = new int [num_users];
		for(int p = 0;p<num_users;p++){
			userBusy[p]=false;
			userDead[p] = false;
			deadCount[p] = 0;
		}
		//System.out.println("\n numusers = "+num_users+" users has some part");
		String line;
		fr  = new FileReader(fil);
		bfd = new BufferedReader(fr);
		while((line = bfd.readLine())!=null){
			st = new StringTokenizer(line);
			//System.out.println("line is "+line+"$");
			if(st.nextToken().equals(InetAddress.getLocalHost().getHostName()))
				continue;
			st.nextToken();
			System.out.println("Total chunks of "+filename +" are "+st.nextToken());
			while(st.hasMoreTokens()){
				String tmp = st.nextToken();
				for(int k =0;k < count;k++){
					//System.out.println("in ");
					if(Integer.parseInt(tmp) == chunkArray[k]){
						rarest[k]++;
						break;
					}
				}
			}
		}
		for(int j = 1; j< count;j++){
			for(int k = j; k > 0; k--){
				if(rarest[k] < rarest[k-1]){
					int temp2 = chunkArray[k-1];
					chunkArray[k-1] = chunkArray[k];
					chunkArray[k] = temp2;
					int temp = rarest[k-1];
					rarest[k-1] = rarest[k];
					rarest[k] = temp;
				}
				else
					break;
			}
		}

		System.out.println("In downloader thread num users"+num_users);
		//System.out.println("In downloader thread totalChunks"+totalChunks);
		//chunkBusy=new boolean[num_users];
		//int next_chunk_to_be=0,next_user_to_be=0,j=0;
		//under_process=new boolean[totalChunks];
		for(i=0;i<max_slots;i++){
			timeout[i]=false;
			slot_free[i]=true;
		}
		chunkRem = count;
		//System.out.println("chunkrem: "+chunkRem);
		int cnt = 0;
		//while there are still chunks to download
		//=> either all chunks (count) are not downlaoded or some chunks can't be downloaded in this session or there are some under process chunks
		while(thrRChunksToDow(count)){
		//while(chunkRem>0){
			for(int k = 0; k < chunkArray.length;k++){
				allSlotsBusy = true;
				for(int y = 0; y < max_slots; y++){
					if(slot_free[y]){
						allSlotsBusy = false;
						break;
					}
				}
				allUserBusy = true;
				for(int p = 0;p<num_users;p++){
					if(!userBusy[p]){
						allUserBusy = false;
						break;
					}
				}

				while(allUserBusy) ;
				while(allSlotsBusy);
				long time;// = System.currentTimeMillis();
				//while((System.currentTimeMillis()-time)<250) ;
				if(!(downloaded[k])&&!(under_process[k])&&(thisSession[k])){
					//System.out.println("calling for chunk " +chunkArray[k]);
					under_process[k] = true;
					cnt++;
					searchHostsWithChunk(filename,k);
					/*time = System.currentTimeMillis();
						while((System.currentTimeMillis()-time)<100);*/
				}
        	}//ends big for
			
		}//ends while
		//System.out.println("called searchHostsWithChunk "+cnt+" number of times. chunkrem is "+chunkRem);
		//System.out.println("printing debug array:");
		/*for(int q=0;q<count;q++)
			System.out.print("debug["+q+"]="+debug[q]);
		System.out.println();*/
		for(int q = 0; q< count; q++){
			if(downloaded[q]){
				String toDelete = ""+chunkArray[q];
				deleteChunk(filename, toDelete);
			}
		}
		System.out.println("Downloading of this file in this session is complete");
		File tempFile = new File(filename+"_temp.txt");
		tempFile.delete(); //uncomment later
		}//ends try 1
		catch(Exception e){System.out.println(e);}
                
	}//ends run
public static void searchHostsWithChunk(String filename,int chunkIndex){
		//System.out.println("In searchHostsWithChunk for filename "+filename+" and chunkIndex "+chunkIndex);
		int userCount = 0, usersWithThisChunk=0, deadCount=0;boolean flag = false;
                try{
                        FileReader in = new FileReader(filename+"_temp.txt");
                        BufferedReader br = new BufferedReader(in);
			String c = ""; int i=0,tok = 0;
			//System.out.println("in srchhost2");
			while((c=br.readLine())!=null && !c.equals("")){
			//System.out.println(c+"printed");
			StringTokenizer st = new StringTokenizer(c);
			//st.nextToken();st.nextToken();st.nextToken();
				String d[] = new String[MAX_CHUNKS];
				while(st.hasMoreTokens()){
					//System.out.println("the user no. "+userCount+" is not busy checking whether he has chunk or not");
					String user = st.nextToken();
					if(user.equals(InetAddress.getLocalHost().getHostName()))
				continue;
					d[i] = user;  i++;
					//System.out.println("d["+i+"]="+d[i-1]);
					if(i>3){
						tok = Integer.parseInt(d[i-1]);
						if(tok==chunkArray[chunkIndex]){
						usersWithThisChunk++;
						if(!(userDead[userCount])){
							flag = true;
							if(!(userBusy[userCount])){
							//start chunk no. k download from d[0] for file in d[1]
							    for(int j=0;j<max_slots;j++)
								if(slot_free[j]){
								System.out.println("Downloading chunk No."+chunkArray[chunkIndex]+" from "+d[0]);
								//under_process[chunkIndex] = true;
								//if(!downloaded[chunkIndex]){
								clt[j] = new client_threads(filename,j,chunkIndex,d[0],userCount);
								userBusy[userCount] = true;
								slot_free[j]=false;
								clt[j].start();
								//}
								//break;
								return;
								}
								/*else if(j==max_slots){
									System.out.println("all slots are busy");
									long time = System.currentTimeMillis();
									while(System.currentTimeMillis()-time<TIMEOUT/2);
									searchHostsWithChunk(filename, chunkIndex);
								}*/
								}//allocated a thread to download the chunk
							    }
							else{
								//System.out.println("user is dead");
								deadCount++;
								}
							break;
							}
						//else if(tok>chunkIndex) break; //the chunks need to be sorted
				}// end st has more token
				
			}
			userCount++; i = 0;//System.out.println("usercount "+userCount);
		}in.close();
		if((!flag)&&(usersWithThisChunk==deadCount)||(usersWithThisChunk == 0)){
			thisSession[chunkIndex]=false;
			System.out.println("this chunk can't be downloaded in this session"+chunkArray[chunkIndex]);
			
		}
		if(flag){
			under_process[chunkIndex] = false;
		}
		}
		catch(Exception e){System.out.println("in searchHostsWithChunk:"+e );}
		/*if(userCount==num_users){
			//chunkBusy[chunkIndex] = true;
			System.out.println("finished hr");
		}*/
		under_process[chunkIndex] = false;
	
	}
	
	public static boolean thrRChunksToDow(int count){
		int i;
		for(i = 0; i < count; i++){
			if(!(downloaded[i])&&thisSession[i]){
				return true;
			}
		}
		//System.out.println("download chunk index: "+i);
		return false;
	
	
	}
    public static void deleteChunk(String filename, String toDelete){
	//System.out.println("inside delete chunk deleteing chunk: "+toDelete);
	File file = new File("unfinishedDownload.txt");
	String last = getDeletedChunk(filename,toDelete, file);
	try{
		//System.out.println("inside delete chunk calling setcontents");
	    setContents(file,last);
	}
	//catch(FileNotFoundException fnfe){ System.out.println(fnfe); }IOException
	catch(IOException fnfe){ System.out.println(fnfe); }
	//System.out.println("Done deleteing chunk: "+toDelete);
    }//done Deleteing
    
        public static String getDeletedChunk(String filename, String toDelete, File file) {
    //...checks on aFile are elided
	        //File file = new File("unfinishedDownload.txt");
                StringBuilder contents = new StringBuilder();
                int count = 0;

            try {
                //use buffering, reading one line at a time
                //FileReader always assumes default encoding is OK!
                BufferedReader br =  new BufferedReader(new FileReader(file));
                //try {
		String  c,d,line=filename, totalChunks;
		while ((c=br.readLine()) !=null)
		{
			StringTokenizer st = new StringTokenizer(c);
			d = st.nextToken();
			if(d.equals(filename)){
				//System.out.println("find line in which chunk has to deleted line is "+line);
				totalChunks = st.nextToken();
				line+=" "+totalChunks; //total chunks wrote
				while ((st.hasMoreTokens())){
					//System.out.println("line is "+line +"to delete is "+toDelete);
	                                if(!((d=st.nextToken()).equals(toDelete))){
	                                		count++;
                                        	line+=" "+d;
						//System.out.println("chunk not to delete is "+d);
					}
				}
				//System.out.println("final line is "+line);
				if(!(count==0)){
				contents.append(line);
                                contents.append(System.getProperty("line.separator"));
				}
                if(count == 0){
			//System.out.println("calling reassembly&&&&&&&&&&&&&&");
                	reassembly(filename,totalChunks);
			}
			}
			else{
				contents.append(c);
	                        contents.append(System.getProperty("line.separator"));
			}
		}
	      }catch(FileNotFoundException fnf){System.out.println("fille"+fnf);}
	       catch( IOException ioe){System.out.println("error"+ioe);}    
	return contents.toString();
	}



public static void setContents(File aFile, String aContents)
                                 throws FileNotFoundException, IOException {
		//System.out.println("inside setcontents method");
                if (aFile == null) {
                        throw new IllegalArgumentException("File should not be null.");
                }
                if (!aFile.exists()) {
                        throw new FileNotFoundException ("File does not exist: " + aFile);
                }
                if (!aFile.isFile()) {
                        throw new IllegalArgumentException("Should not be a directory: " + aFile);
                }
                if (!aFile.canWrite()) {
                        throw new IllegalArgumentException("File cannot be written: " + aFile);
                }

    //use buffering
                Writer output = new BufferedWriter(new FileWriter(aFile));
                try {
                        //FileWriter always assumes default encoding is OK!
                        output.write( aContents );
			//System.out.println("contents of file "+aContents+"$$");
                }
                finally {
                        output.close();
                }
        }
        
    public static void reassembly(String filename, String totalChunks){
    System.out.println("All chunks downloaded. Now Reassembling the file...");
    	int totalchunks = Integer.parseInt(totalChunks);
    	byte [] bytes = new byte [1024*1024];
	byte []bytes2;
    	int io = -1;
    	 try {
	        File file = new File(filename);
	        if(file.exists())
	        	file.delete();
			file.createNewFile();
		FileOutputStream fp = new FileOutputStream(file,true);
		deleteSearchLine("unfinishedDownload.txt", filename);
		int off = 0; 
    	for(int i = 0; i<totalchunks;i++){
    		
		File temp = new File(filename+"_chunk_"+i+".txt");
		//File tempSha =  new File(filename+"_"+i+"_sha.txt");
    	FileInputStream fstream = new FileInputStream(filename+"_chunk_"+i+".txt");
    	DataInputStream in = new DataInputStream(fstream);
		io = in.read(bytes,0,1024*1024);
			
			if(io!= -1){
				bytes2 = new byte[io];
				for(int a=0;a<io;a++)
					bytes2[a] = bytes[a];
				
				//System.out.println("io is "+io);
				fp.write(bytes2);
	    		}
		//System.out.println("in for offset is "+off);
	    	off = off + io;
		temp.delete();
	//System.out.println("offset final is "+off);
    	}
	fp.close();
	System.out.println("Reassembled Successfully");
	}catch(Exception e){}//System.out.println("in reasembly"+e);}
    }
	public static synchronized void shareChunk(String filename,int chunk, DatagramSocket socket) throws IOException{
	File file =  new File("shared.txt");
	System.out.println("Sharing chunk No. "+chunk);
	BufferedReader in =  new BufferedReader(new FileReader(file));
	String line = "", toWrite="";
	boolean first = true;
	StringTokenizer st;
	boolean nothing = true;
	while((line=in.readLine())!=null&&!(line.equals(""))){
		nothing = false;
		st = new StringTokenizer(line);
		if(filename.equals(st.nextToken())){
			String temp="";
			String more = "";
			first = false;
			boolean flag = false;
			boolean flag1 = false;
			line = filename + " " + st.nextToken();
			temp = st.nextToken();
			while(st.hasMoreTokens()){
				if(chunk  > Integer.parseInt(temp)){
					line += " " + temp;
				}
				else if(chunk == Integer.parseInt(temp));
				else{
					flag = true;
					line += " " + chunk;
					break;
				}
				temp = st.nextToken();
				if(filename.equals(temp)){
					flag1 = true;
					break;
				}
			}
			if(flag){
				line += " " + temp;
				while(st.hasMoreTokens()){
					line  += " " + st.nextToken();
				}
			}
			else{
				line += " " + chunk;
				line += " " +filename;
			}
		}
		if(toWrite.equals(""))
			toWrite = line;
		else
			toWrite +="\n" + line;
	}
	if(first){
		if(!nothing)
		toWrite += "\n"+filename +" "+totalChunks+" "+chunk+" "+filename;
		else
		toWrite += filename +" "+totalChunks+" "+chunk+" "+filename;
		String Line = Client.CmpltLine("main.txt",filename);
		Client.incrementCount("main.txt",filename);
		String increment=Peer.magic + " "+Peer.fileUploadStr+" "+Line;//00 is used for file request
		byte smallMsg[] = new byte[1024];
		DatagramPacket smallPack = new DatagramPacket(smallMsg, smallMsg.length);
		smallPack.setData(increment.getBytes());
		Client.copy("users.txt","temp.txt");
		System.out.println("Broadcasting sharing of "+filename);
		Cubbyhole cb = new Cubbyhole();
		Broadcast broadcast = new Broadcast(cb, socket);
		broadcast.start();
		cb.put(smallPack);
	}
	setContents(file, toWrite);
	in.close();
}



	public static void deleteSearchLine(String filename, String lineWithFilename){
		//System.out.println("in deletesrchline");
		try{
		File file = new File(filename);
		String last = getDeletedSearchLine(file,lineWithFilename);
		setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done decrementing

	public static String getDeletedSearchLine(File aFile, String lineWithFilename) {
		//System.out.println("inside deletesearchline method");
    //...checks on aFile are elided
		StringBuilder contents = new StringBuilder();
		boolean toAdd = true;
		try {
		//use buffering, reading one line at a time
		//FileReader always assumes default encoding is OK!
		BufferedReader input =  new BufferedReader(new FileReader(aFile));
		try {
			String line = null; //not declared within while loop
			while (( line = input.readLine()) != null){
				StringTokenizer st = new StringTokenizer(line);
				toAdd = true;
				if(st.nextToken().equals(lineWithFilename)){
						toAdd = false;
				}
				if(toAdd){
					contents.append(line);
					contents.append(System.getProperty("line.separator"));
				}
			}
		}
		finally {
			input.close();
		}
	}
	catch (IOException ex){
		ex.printStackTrace();
	}
	return contents.toString();
	}	
		
		//	decrement(lineWithFilename.substring(0,i),"main.txt");
		//if the count become 0 delete that entry completely






}//ends controller thread



   
     


class client_threads extends Thread{
	public String filename;

    public static String magic = "1234";
    public static String chunkDowStr = "14";
 private boolean downloadComplete=false;
	public int chunk;
    private int indx;
 public int userCount;
	public String user;
	public int index;
	public int userid;
	public client_threads(String filename,int slot,int chunkIndex, String user, int userCount){
	this.filename=filename;
	this.user=user;
	this.indx=chunkIndex;//chunarray index
	this.index = slot;//thread index
	chunk = Downloader.chunkArray[chunkIndex];
	this.userCount = userCount;
    }
	public void run(){	
		try{
String st=null;	
			//System.out.println("in client_thread :"+index);
			Downloader.slot_free[index]=false;
			Downloader.userBusy[userCount]=true;
			int server_port=Peer.SPORT;
	    int client_port=Peer.CPORT;

			int LFR=-1;//last frame received 
        		int RWS=8;//receiver window size
        		int SNTA=0;//sequence no. to be acked
       			int LAS=RWS+LFR;//last acceptable sequence no.



	    
	    DatagramSocket socket = new DatagramSocket(client_port+index+5);

// set up client socket for listening and sending 
	    
        		int buffull=0;
       			boolean alreadypresent=false;
			boolean errFlag=false;
			boolean downloadComplete=false;
			boolean sentAllPackets=false;
			DatagramPacket []PacketBuff=new DatagramPacket[RWS];
			String SeqNo="";
			int SN=-1;
			boolean flag=false;
			int serverHandlerPort=0;
			boolean closedfile=true;
			int k=0;
			while(k<RWS){
				PacketBuff[k]=null;
				k++;
			}

			byte [] buf1=new byte[1024];
			byte [] buf=new byte[1024];
	        	try {
    				socket.setSoTimeout(1000);//if receiver is down then generate an exception
			}
			catch (SocketException e) {
    				System.out.println("Error setting timeout TIMEOUT: " + e);
			}
			InetAddress dir=InetAddress.getByName(user);		
			DatagramPacket packet=null;
		        		
	    String entry=magic+" "+chunkDowStr+" "+filename+" "+chunk;
	    packet = new DatagramPacket(buf,buf.length);
	    packet.setData(entry.getBytes());
	    packet.setAddress(dir);
	    packet.setPort(server_port);//server will send data on different ports not where it is a regular listener
	    //also if it will serve many client at the same time then all will be served at different ports
	    //packet = new DatagramPacket(buf1, io, dir,server_port);
	    try{
		socket.send(packet);
	    }
	    catch(Exception err){ System.out.println(err);
	    }


	    FileOutputStream outFile;
	    outFile = new FileOutputStream(filename+"_chunk_"+(chunk)+".txt");//or filename_chunknumber.txt
            DatagramPacket ack=new DatagramPacket("0000".getBytes(),4);
	    ack.setAddress(dir);
	    int port = 0;
	    socket.setSoTimeout(2000);
	    
			byte [] buf2=new byte[1024];
			DatagramPacket packet2 = new DatagramPacket(buf2,buf2.length);
			
			int p1=0; 
			while(true){
				byte []buffer=new byte[1024];
				DatagramPacket P = new DatagramPacket(buffer,buffer.length);
				try{
					socket.receive(P);
					serverHandlerPort=P.getPort();
					Downloader.deadCount[userCount]=0;	
				}				
				catch(SocketTimeoutException te){
					socket.close();
					
					if(downloadComplete){
						Downloader.downloaded[indx]=true;
						Downloader.under_process[indx]=false;
						Downloader.userBusy[userCount]=false;
						Downloader.timeout[index]=true;				
						break;
					}
					if(Downloader.deadCount[userCount] == 3)
	           	 	Downloader.userDead[userCount] = true;
				  	  else{
					Downloader.deadCount[userCount]++;				
					//System.out.println("deadcount increased");
					}
					//System.out.println("thread terminated : "+index);
					Downloader.userBusy[userCount]=false;
					Downloader.timeout[index]=true;					
					errFlag=true;
					break;
				}

				Downloader.userBusy[userCount]=true;
				//System.out.println("received packet with sn : "+getSN(P));
				if(downloadComplete){
                                      	if(LFR/1000!=0)SeqNo=""+LFR;
                                      	else if(LFR/100!=0)SeqNo="0"+LFR;
                                       	else if(LFR/10!=0)SeqNo="00"+LFR;
                                       	else SeqNo="000"+LFR;
					byte []sn=new byte[4];
					copySN(sn,SeqNo);
                                       	ack=new DatagramPacket(sn,4,dir,serverHandlerPort);
      			       		socket.send(ack);
									
				}
				else{
					SN=getSN(P);
					if(SN>LFR && SN<=LAS){						
							PacketBuff[SN-SNTA]=P;
							int l=0;
							DatagramPacket temp=PacketBuff[l];
							while(l<RWS && temp!=null){							
								st=new String(temp.getData());
								
								if(st.charAt(4)=='@' && st.charAt(5)=='#' && st.charAt(6)=='$'){//check whether end of transfer,last packet					
									downloadComplete=true;
								//Downloader.shareChunk(filename,chunk,socket);
								Downloader.downloaded[indx]=true;
				    			Downloader.under_process[indx] = false;
								Downloader.shareChunk(filename,chunk,socket);
									//System.out.println("end packet received");								
									SNTA++;
									l++;
									break;
								}
								else{
									//copy the data part
									byte []data=new byte[temp.getLength()-4];
									int j=0;
									while(j<temp.getLength()-4){
										data[j]=temp.getData()[j+4];
										j++;
									}
									outFile.write(data,0,temp.getLength()-4);
									PacketBuff[l]=null;
									SNTA++;
									l++;					
								}
								if(l<RWS)temp=PacketBuff[l];
							}
							if(l!=0)LFR=SNTA-1;
							int l1=0;
							while(l<RWS){						
								PacketBuff[l1]=PacketBuff[l];
								l++;
								l1++;
							}
							while(l1<RWS){						
								PacketBuff[l1]=null;
								//l++;
								l1++;
							}
							p1++;							
							LAS=LFR+RWS;
							if(LFR/1000!=0)SeqNo=""+LFR;
                                                	else if(LFR/100!=0)SeqNo="0"+LFR;
                                                	else if(LFR/10!=0)SeqNo="00"+LFR;
                                                	else SeqNo="000"+LFR;
							byte []sn=new byte[4];
							copySN(sn,SeqNo);
                                               		ack=new DatagramPacket(sn,4,dir,serverHandlerPort);
							//System.out.println("send an ack with sn : "+LFR);
      			       				if(LFR!=-1)socket.send(ack);
						
					}
					else{//reject the packet
					}
				}
			}
	       	 	try{
				flag=true;
       				outFile.close();
       				socket.close();
			}
			catch (Exception err1){//Catch exception if any
 				 System.err.println("Error: " + err1.getMessage());
   			}
			
			Downloader.userBusy[userCount]=false;
			Downloader.slot_free[index]=true;
			Downloader.under_process[indx]=false;	   
	    	Downloader.allSlotsBusy = false;
	    	Downloader.allUserBusy = false;


		}
		catch(Exception sdgds){}      		
	}
	
     	public int getSN(DatagramPacket P){
                //read first 4 bytes of data and get SN
                byte []st=P.getData();
                byte []SN=new byte[4];
                SN[0]=st[0];
                SN[1]=st[1];
                SN[2]=st[2];
                SN[3]=st[3];
                String SeqN=new String(SN);
                int seq=Integer.parseInt(SeqN);
                return seq;
     	}
	public void copySN(byte[] buf,String SN){
		int i=0;
		char d=0;
		while(i<4){
			d=SN.charAt(i);
			switch (d) {
				case '0': buf[i]='0';break;
				case '1': buf[i]='1';break;
				case '2': buf[i]='2';break;
				case '3': buf[i]='3';break;
				case '4': buf[i]='4';break;
				case '5': buf[i]='5';break;
				case '6': buf[i]='6';break;
				case '7': buf[i]='7';break;
				case '8': buf[i]='8';break;
				case '9': buf[i]='9';break;
				default: buf[i]='\0';break;
			}
			i++;
		}	
	}
         public boolean probability(){
		if(Math.random()<Server.send_prob)return true;
		else return false;
	}





/*
    


   catch (Exception err1){
		System.err.println("Error: " + err1.getMessage());
	    }
	    
	//System.out.println("Set users as not busy slot is also made free");
	}
	catch(Exception e){}
	//System.out.println("finally exiting from thread");	
    }//end run

    public int getSN(DatagramPacket P){
	//read first 4 bytes of data and get SN
	byte []st=P.getData();
	byte []SN=new byte[4];
	SN[0]=st[0];
	SN[1]=st[1];
	SN[2]=st[2];
	SN[3]=st[3];
	String SeqN=new String(SN);
	int seq=Integer.parseInt(SeqN);
	return seq;
    }
    */
//deleteChunk("unfinishedDownload.txt",filename,chunk);

//deleteChunk("main.txt",filename.substring(0,j));//Delete count for thisshared file
        //this method is also used in server b/c he also do these when receive such request.
public static void setContents(File aFile, String aContents)
                                 throws FileNotFoundException, IOException {
		//System.out.println("inside setcontents method");
                if (aFile == null) {
                        throw new IllegalArgumentException("File should not be null.");
                }
                if (!aFile.exists()) {
                        throw new FileNotFoundException ("File does not exist: " + aFile);
                }
                if (!aFile.isFile()) {
                        throw new IllegalArgumentException("Should not be a directory: " + aFile);
                }
                if (!aFile.canWrite()) {
                        throw new IllegalArgumentException("File cannot be written: " + aFile);
                }

    //use buffering
                Writer output = new BufferedWriter(new FileWriter(aFile));
                try {
                        //FileWriter always assumes default encoding is OK!
                        output.write( aContents );
			//System.out.println("contents of file "+aContents+"$$");
                }
                finally {
                        output.close();
                }
        }



    private static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
        	int halfbyte = (data[i] >>> 4) & 0x0F;
        	int two_halfs = 0;
        	do {
	            if ((0 <= halfbyte) && (halfbyte <= 9))
	                buf.append((char) ('0' + halfbyte));
	            else
	            	buf.append((char) ('a' + (halfbyte - 10)));
	            halfbyte = data[i] & 0x0F;
        	} while(two_halfs++ < 1);
        }
        return buf.toString();
    }
 
    public static String SHA1(String text) 
    throws NoSuchAlgorithmException, UnsupportedEncodingException  {
	MessageDigest md;
	md = MessageDigest.getInstance("SHA");
	byte[] sha1hash = new byte[40];
	md.update(text.getBytes(), 0, text.length());
	sha1hash = md.digest();
	return convertToHex(sha1hash);
    }
	public static void deleteSearchLine(String filename, String lineWithFilename){
		try{
		File file = new File(filename);
		String last = getDeletedSearchLine(file,lineWithFilename);
		setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done decrementing

	public static String getDeletedSearchLine(File aFile, String lineWithFilename) {
		//System.out.println("inside deletesearchline method");
    //...checks on aFile are elided
		StringBuilder contents = new StringBuilder();
		boolean toAdd = true;
		try {
		//use buffering, reading one line at a time
		//FileReader always assumes default encoding is OK!
		BufferedReader input =  new BufferedReader(new FileReader(aFile));
		try {
			String line = null; //not declared within while loop
			while (( line = input.readLine()) != null){
				StringTokenizer st = new StringTokenizer(line);
				toAdd = true;
				if(st.nextToken().equals(lineWithFilename)){
						toAdd = false;
				}
				if(toAdd){
					contents.append(line);
					contents.append(System.getProperty("line.separator"));
				}
			}
		}
		finally {
			input.close();
		}
	}
	catch (IOException ex){
		ex.printStackTrace();
	}
	return contents.toString();
	}	
		

}//ends class



class Cubbyhole {//used for interthread communication
    private DatagramPacket contents;
    private boolean available = false;

    public synchronized DatagramPacket get() {
        while (available == false) {
            try {
                wait();
            } catch (InterruptedException e) { }
        }
        available = false;
        notifyAll();
        return contents;
    }

    public synchronized void put(DatagramPacket value) {
        while (available == true) {
            try {
                wait();
            } catch (InterruptedException e) { }
        }
        contents = value;
        available = true;
        notifyAll();
    }
}

class Broadcast extends Thread{ //broad cast to all server with name in temp.txt
	Cubbyhole cb1;
	DatagramSocket sock;
	public Broadcast(Cubbyhole cb1, DatagramSocket sock){
	   this.cb1 = cb1;
	  this.sock=sock;
	}
	public void run(){
		while(true){
			int count =1;
			DatagramPacket pack = cb1.get();
			//System.out.println("Got a brdcst request. Packet contents are "+new String(pack.getData()));
			for(int i = 0; i < count; i++){
				try{
				File userList = new File("temp.txt");
				FileReader userlist = new FileReader(userList);
				BufferedReader br = new BufferedReader(userlist);
				// Continue to read lines while
				// there are still some left to read
				String  c;
				while((c = br.readLine()) != null){
					
					if(c.equals(InetAddress.getLocalHost().getHostName()))
						continue;
					//System.out.println(" broadcasting "+new String(pack.getData())+" to "+c);
					try{
					pack.setPort(Peer.SPORT);
					InetAddress address = InetAddress.getByName(c);
					pack.setAddress(address);
					}catch(UnknownHostException uhe){}
					try{
						sock.send(pack);
					}catch (IOException ex) {System.err.println(ex);}
				}
				//System.out.println ("Waiting for return packet inside broadcasting");
				//try {
					int timeout = 500;long time = System.currentTimeMillis();
					while((System.currentTimeMillis()-time)<timeout){}//do nothing wait
					//sock.setSoTimeout(500);

				//}
				//catch (SocketTimeoutException ste)
         			//{
         			//	System.out.println ("Timeout Occurred: Packet assumed lost");
         			//	System.out.println ("message re-attempt" + i);
				//}
				br.close();
			}catch(FileNotFoundException fnfe){System.out.println("Broadcast: "+fnfe);}
			catch(IOException ioe){System.out.println("Broadcast: "+ioe);}
			}
		}
	}
}





class Server extends Thread {
	private static final String magic = "1234";
	private final static int newUserStr = 0;//making them integer
	private static String newUserRepStr =      "1";
	private final static int beaconStr =          2;
	private static String beaconRepStr =       "3";
	private final static int updateStr =          4;
	private static String updateboth =         "5";
	private static String updateUsers =        "6";
	private static String updateMain =         "7";
	private static String allreadyUpdated =    "8";
	private final static int sendUserStr =        9;
	private final static int sendMainStr =        10;
	private final static int fileReqStr =         11;
	private static String YfileReqRepStr =     "12";
	private static String NfileReqRepStr =     "13";
	private final static int chunkDowStr =        14;
	private final static int fileUploadStr =      15;
	private final static int newfileUploadStr =   16;
	private static String newfileUploadRepStr= "17";
	private static String fileUploadRepStr =   "19";
	private final static int fileDeleteStr =      22;
	private static String fileDeleteRepStr =   "23";
	private final static int fileDownloadedAck = 24;
	public static String updatemasterChunk =         "26";
	public final static int sendMasterStr  =   28;
	public final static int max_slots = 4;
	public static double send_prob = 1;
	public static boolean[] free_slot = new boolean[max_slots]; 
	private static File users = new File("users.txt");
	private static File main = new File("main.txt");
	private static File master = new File("masterChunk.txt");
	private static DatagramPacket pack;
	private static DatagramSocket sock;
	
	public Server(){
	}

	public void run() {
		for(int free = 0; free < max_slots; free++){
			free_slot[free] = true;
		}
		try{
			sock = new DatagramSocket(Peer.SPORT);
			sock.setSoTimeout(100000);
		}
		catch (SocketException se) {System.err.println(se);}
		while(true){
			byte[] receiveData = new byte[1024];
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {	
				sock.setSoTimeout(100000);
				sock.receive(receivePacket);
          		}
      			catch (SocketException se) {System.err.println(se);}
          		catch(IOException e){ }
			String ans = new String(receivePacket.getData());
			int i=0;
			ans = ans.trim();
			StringTokenizer st = new StringTokenizer(ans);
			String array[] = new String[5];
			byte[] buf = new byte[1024];
			while (st.hasMoreTokens()) {
				array[i] = st.nextToken();
				//System.out.println("value in arrays are:"+array[i]);
				i++;
			}
			String str = array[1];
			if(array[0]==null)
				continue;
			if(array[0].equals(magic)){
				//System.out.println("switching on int value:"+array[1]+"$$");
				int sw = Integer.parseInt(str);
				switch(sw){
					case newUserStr:
						//System.out.println("server got a newuser");
						String qw = receivePacket.getAddress().getHostName().toString();
						if(!searchLine("users.txt",qw));
							append("users.txt",qw);
						String temp = array[0] + " " + newUserRepStr;
						buf = temp.getBytes();
						pack=new DatagramPacket(buf,buf.length,receivePacket.getAddress(), receivePacket.getPort());
						try{
							sock.send(pack);
						}catch(IOException ioe){}
						break;
					case beaconStr:
						//System.out.println("Server:got a beacon from "+receivePacket.getAddress());
						temp = array[0] + " " + beaconRepStr ;
						buf = temp.getBytes();
						pack = new DatagramPacket(buf,buf.length, receivePacket.getAddress(), receivePacket.getPort());
						try{
							sock.send(pack);
						}catch(IOException ioe){}
						break;
					case updateStr:
						//System.out.println("server got a compare update:"+array[2].substring(0,13)+"k");
						long updatedusers =Long.parseLong(array[2].substring(0,8)); 
						long updatedmain =Long.parseLong(array[3].substring(0,8)); 
						//long updatedmaster =Long.parseLong(array[4].substring(0,8)); 
						//System.out.println("his updated time of users:"+updatedusers+"main:"+updatedmain);
						long myUpdateusers =Long.parseLong((Long.toString(users.lastModified())).substring(0,8)); 
						long myUpdatemain =Long.parseLong((Long.toString(main.lastModified())).substring(0,8));
						//long myUpdatemaster =Long.parseLong((Long.toString(master.lastModified())).substring(0,8));
						//System.out.println("my updated time of master:"+myUpdatemaster+"master:"+myUpdatemaster);
						if(myUpdateusers > updatedusers){
							if(myUpdatemain > updatedmain)
								temp = array[0] + " " + updateboth;
							else
								temp = array[0] + " " + updateUsers;
						}
						else if(myUpdatemain > updatedmain)
							temp = array[0] + " " + updateMain;
						else
							temp = array[0] + " " + allreadyUpdated;
						buf = temp.getBytes();
						pack = new DatagramPacket(buf,buf.length, receivePacket.getAddress(), receivePacket.getPort());
						try{
							sock.send(pack);
						}catch(IOException ioe){}
						break;
					case sendMainStr:
						//System.out.println("Server:got a sendmain request");
						sendfile(sock,"main.txt",receivePacket.getAddress());
						break;
					case sendUserStr:
						//System.out.println("Server:got a send user.txt request");
						sendfile(sock,"users.txt", receivePacket.getAddress());
						break;
					case sendMasterStr:
						//System.out.println("Server:got a sendmasterChunk request");
						sendfile(sock,"masterChunk.txt",receivePacket.getAddress());
						break;
					case fileReqStr:
						System.out.println("in the corect case at least with filename "+array[2]+"$");
						String line = RetLineSearch("shared.txt",array[2]);//return null or that line
						String tempLine = "";
						if(line == null)
							temp = array[0] + " " + NfileReqRepStr;
						else{
						String[] result = line.split("\\s");
						for (int x=0; x<result.length - 1; x++){
							//System.out.println(result[x]);
							tempLine+= result[x]+" ";
						}
						temp = array[0] + " " + YfileReqRepStr + " " + tempLine;
						}
						
						buf = temp.getBytes();
						pack = new DatagramPacket(buf,buf.length,receivePacket.getAddress(), receivePacket.getPort());
						try{
							sock.send(pack);
						}catch(IOException ioe){}
						break;
					case chunkDowStr://array[2] must have filename and array[3] have chunk number
						String filename = array[2];
						int chunkNumber = Integer.parseInt(array[3]);
						for(int free = 0;free < max_slots; free++){
						if(free_slot[free]){
						free_slot[free] = false;
						ServerReqHandler s = new ServerReqHandler(receivePacket.getAddress(),receivePacket.getPort(), filename, chunkNumber, free);
						s.start();//it runs on different port so not interfere with server other works
						break;
						}
						}
						break;
					case newfileUploadStr:
						append("main.txt",array[2] + " " + 1);
						temp = array[0] + " " + newfileUploadRepStr;
						buf = temp.getBytes();
						pack = new DatagramPacket(buf,buf.length,receivePacket.getAddress(),receivePacket.getPort());
						try{
						sock.send(pack);
						downloadmasterChunk(sock,receivePacket.getAddress());
						}catch(IOException ioe){}
						break;
					case fileUploadStr:
						if(searchLine("main.txt",(array[2] + " " +array[3])))
							incrementCount("main.txt",array[2]);
						temp = array[0] + " " + fileUploadRepStr;
						buf = temp.getBytes();
						pack = new DatagramPacket(buf,buf.length,receivePacket.getAddress(), receivePacket.getPort());
						try{
							sock.send(pack);
						}catch(IOException ioe){}
						break;
					case fileDeleteStr:
						if(searchLine("main.txt",(array[2] + " " + array[3])))
							decrementCount("main.txt", array[2]);
						temp = array[0] + " " + fileDeleteRepStr;
						buf = temp.getBytes();
						pack = new DatagramPacket(buf,buf.length,receivePacket.getAddress(), receivePacket.getPort());
						try{
							sock.send(pack);
						}catch(IOException ioe){};
						break;
					case fileDownloadedAck:
						//System.out.println("file "+array[2]+" is recvd by "+receivePacket.getAddress());
				}//end switch
			}//end if
		}//end while(true)
	}//end run
	public static String RetLineSearch(String f, String toSearch){
		try{
		File file = new File(f);
		FileReader in = new FileReader(file);
		BufferedReader br = new BufferedReader(in);
		// Continue to read lines while
		// there are still some left to read
		String  c,d;
		while ((c=br.readLine()) !=null)
		{
			StringTokenizer st = new StringTokenizer(c);
			while ((st.hasMoreTokens())){
				d = st.nextToken();
				if(d.equals(toSearch))
					return c;
			}
		}}catch(FileNotFoundException fnf){System.out.println("fille"+fnf);}
		catch( IOException ioe){System.out.println("error"+ioe);}
		return null;
	}

	public static void sendfile(DatagramSocket sock,String filename, InetAddress addr){
        	byte bytes[] = new byte[1600];
		try{
		File inputFile = new File(filename);
		InputStream in = new FileInputStream(inputFile);
		int io;
		while ((io = in.read(bytes, 0, 1600)) > 0){
			DatagramPacket packet;
			if(io < 1600){
				byte newbuf[] = new byte[io];
				int i = 0;
				while(i < io){
					newbuf[i] = bytes[i];
					i++;
				}
				//System.out.println ("Sending data " + io +  " bytes to "+addr);
				packet = new DatagramPacket(newbuf,io, addr,Peer.CPORT);
			}
			else
				packet = new DatagramPacket(bytes,io,addr,Peer.CPORT);
			boolean lost = true;
			byte[] receiveData = new byte[1600];
			int i=0;
			while(lost==true)
			{
				try{
					//System.out.print("Server:In sendfile. Now sending "+new String(bytes));
					sock.send(packet);
				}
				catch (IOException ex){ System.err.println(ex);}
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				try {
					sock.setSoTimeout(5000);
					sock.receive(receivePacket);
					InetAddress returnIPAddress = receivePacket.getAddress();  // get ipddress
					//int port = receivePacket.getPort();  // get port number
					//System.out.println ("Server:Waiting for return packet in sendfile method from host at: " + returnIPAddress);
					lost = false;
				}
				catch (SocketTimeoutException ste){
					System.out.println ("Timeout Occurred: Packet assumed lost");
					//System.out.println ("Server: message "+" re-attempt no." + i);
					lost = true;
					i++;
					if(i==5){	
						String set="010";
						pack.setData(set.getBytes());
						pack.setPort(Peer.CPORT);
					return;
					}
				}//end catch
				catch (UnknownHostException ex) { System.err.println(ex);}
				catch (SocketException se) {
					System.err.println(se);
				}
				catch (IOException e) {
					System.err.println("Packet " + i + " failed with " + e);
				}
			}//end while
		}//end while
		bytes = "@#$".getBytes();
		DatagramPacket packet = new DatagramPacket(bytes, bytes.length, addr,Peer.CPORT);
		try{
		//System.out.print("Server:In sendfile. Now sending "+new String(bytes));
			sock.send(packet);
		}
		catch (IOException ex){ System.err.println(ex);}
		}catch(IOException ioe){}
	}//end method sendfile

        public static boolean downloadmasterChunk(DatagramSocket sock, InetAddress addr){
                //first send download request   
                int count =0;//send file download request 4 times
                boolean notStarted = true, flag = false;
                String hdr;
                while((count < 4)&&(notStarted)){
                        count++;
                        try{
                                byte[] buf = new byte[1600];
                                byte[] receiveData = new byte[1600];
                                File fp1 = new File("masterChunk.txt");
                                FileOutputStream fp = new FileOutputStream("masterChunk.txt");      
                                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);  // 5. create datagram packet for incoming         datagram
                              //size of file
                                int size = 0;
                                while(size<5){//max downloadable size is 10 kb
                                        size++;
                                        System.out.println("file size"+fp1.length());
                                        sock.setSoTimeout(1000);
                                        try {//you may also check whether it is correct file packet or something else
                                            sock.receive(receivePacket);  
                                            InetAddress returnIPAddress = receivePacket.getAddress();
                                                int port = receivePacket.getPort();  // get port number
                                                //System.out.println ("From server at: " + returnIPAddress +  ":" + port);
                                        //System.out.println("Message: " + modifiedSentence);
						String d = new String(receivePacket.getData());
						d = d.trim();
                                                String see = new String(receivePacket.getData());
                                                see = see.trim();
                                                //System.out.println("inside the packet"+see); 
                                                //System.out.println(" downloading file   \n");
                                                notStarted = false;
                                                String ackn="ack";
                                                byte[] buff = new byte[1600];
                                                buff = ackn.getBytes();
                                                DatagramPacket pkt = new DatagramPacket(buff, buff.length, returnIPAddress,port);
                                                sock.send(pkt);
                                                //System.out.println("ack sent \n");
                                                if(see.charAt(0)=='@' && see.charAt(1)=='#' && see.charAt(2)=='$'){  //last packet	       
         		        			flag =true;
         						break;
         					}
						else
							 fp.write(d.getBytes());
                                        }
                                catch (SocketTimeoutException ste)
                                {
                                        System.out.println ("Timeout Occurred: Packet assumed lost");}
                        }//end while 
                        fp.close();
                }//end try
                catch (UnknownHostException ex) { System.err.println(ex);}
                catch (IOException ex) {System.err.println(ex);}
                catch (Exception e){System.err.println("File input error");}

                }//end while
		if(flag){
				byte[] buf2 = new byte[1600];
				buf2 = (Peer.magic +" "+Peer.fileDownloadedAck+" "+"masterChunk.txt").getBytes();

				try{
				//InetAddress addr = InetAddress.getByName(address);
				DatagramPacket ackpack = new DatagramPacket(buf2, buf2.length, addr, Peer.CPORT);
				sock.send(ackpack);
				} catch(Exception e){
					System.out.println(e);
				}
		}
                return flag;
        }//end method



	public static boolean searchLine(String file, String query){
		try{
			FileInputStream fstream = new FileInputStream(file);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   {
				// Print the content on the console
				if(strLine.equals(query)){
					in.close();
					//System.out.print("dunno");
					return true;
				}
				//System.out.print(strLine);
			}
			//Close the input stream
			in.close();
		}catch (Exception e){System.err.println("Error: " + e.getMessage());}
		return false;
	}//end method

	public static void append(String filename, String toAppend){
		try{
			File file = new File(filename);
			String last = getAppendedContents(file,toAppend);
			//System.out.println("inside append server calling set contents");
			setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done appending

	public static String getAppendedContents(File aFile, String toAppend) {
		StringBuilder contents = new StringBuilder();
		try {
			BufferedReader input =  new BufferedReader(new FileReader(aFile));
			try {
				String line = null;
				while (( line = input.readLine()) != null){
					contents.append(line);
					contents.append(System.getProperty("line.separator"));
				}
				contents.append(toAppend);
				contents.append(System.getProperty("line.separator"));
			}
			finally {
				input.close();
			}
		}
		catch (IOException ex){ex.printStackTrace();}
		return contents.toString();
	}
	
	public static void setContents(File aFile, String aContents)throws FileNotFoundException, IOException {
		if (aFile == null) {
			throw new IllegalArgumentException("File should not be null.");
		}
		if (!aFile.exists()) {
			throw new FileNotFoundException ("File does not exist: " + aFile);
		}
		if (!aFile.isFile()) {
			throw new IllegalArgumentException("Should not be a directory: " + aFile);
		}
		if (!aFile.canWrite()) {
			throw new IllegalArgumentException("File cannot be written: " + aFile);
		}
		Writer output = new BufferedWriter(new FileWriter(aFile));
		try {
			output.write( aContents );
		}
		finally {
			output.close();
		}
	}//end method
	public static void incrementCount(String filename, String toIncrement){
		try{
		File file = new File(filename);
		String last = getIncrementedContents(file,toIncrement);
		setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done incrementing

	public static String getIncrementedContents(File aFile, String toIncrement) {
		StringBuilder contents = new StringBuilder();
		int initialCount, finalCount;
		try {
		BufferedReader input =  new BufferedReader(new FileReader(aFile));
			try {
				String line = null; //not declared within while loop
				while (( line = input.readLine()) != null){
					StringTokenizer st = new StringTokenizer(line);
					if(st.nextToken().equals(toIncrement)){
						initialCount = Integer.valueOf( st.nextToken()).intValue();
						finalCount = initialCount + 1;
						line = toIncrement + " " + finalCount;
					}
					contents.append(line);
					contents.append(System.getProperty("line.separator"));
				}
			}
			finally {
				input.close();
			}
		}
		catch (IOException ex){ex.printStackTrace();}
		return contents.toString();
	}	
	
	public static void decrementCount(String filename,String toDecrement){
		try{
			File file = new File(filename);
			String last = getDecrementedContents(file,toDecrement);
			setContents(file,last);
		}catch(FileNotFoundException fnfe){}
		catch(IOException ioe){}
	}//done decrementing
	
	public static String getDecrementedContents(File aFile, String toDecrement) {
		StringBuilder contents = new StringBuilder();
		int initialCount, finalCount;
		boolean toAdd = true;
		try {
		//use buffering, reading one line at a time
		//FileReader always assumes default encoding is OK!
			BufferedReader input =  new BufferedReader(new FileReader(aFile));
			try {
				String line = null; //not declared within while loop
				while (( line = input.readLine()) != null){
					StringTokenizer st = new StringTokenizer(line);
					toAdd = true;
					if(st.nextToken().equals(toDecrement)){
						initialCount = Integer.valueOf( st.nextToken()).intValue();
						finalCount = initialCount - 1;
						if(finalCount > 0)
							line = toDecrement + " " + finalCount;
						else
							toAdd = false;
					}
					if(toAdd){
						contents.append(line);
						contents.append(System.getProperty("line.separator"));
					}
				}
			}
			finally {
				input.close();
			}
		}
		catch (IOException ex){ex.printStackTrace();}
		return contents.toString();
	}
}//end server thread

class ServerReqHandler extends Thread{
	public InetAddress dir;
	public static int port;
	public int chunkIndex;
	public String filename;
	public static int SPORT = 2364;
	public static String magic  = "1234";
	public static String chunkRepStr = "27";
	private int index;
	private int client_port;
	public ServerReqHandler(InetAddress address, int client_port, String filename, int chunkIndex, int index){
		this.dir=address;
		this.chunkIndex=chunkIndex;
		this.filename = filename;
		this.index=index;
		this.port = client_port;
	}
	public void run(){
		Server.free_slot[index] = false;
		try{
                  DatagramSocket socket = new DatagramSocket(Peer.SPORT+index+10);
                  try {
    					socket.setSoTimeout(100);
				}
				catch (SocketException e) {
    				System.out.println("Error setting timeout TIMEOUT: " + e);
				}
                  String date = DateUtils.now();
                  File log = new File("server.log");
                  log.createNewFile();
                  BufferedWriter out = new BufferedWriter(new FileWriter("server.log",true));
                        out.write("*****************"+date+"*****************");
			byte[] waste = new byte[1024*1024];
			byte[] buf = new byte[1024];
			byte[] buf4=new byte[1020];
			byte[] buf5 = new byte[20];
			byte []sn=new byte[4];
			byte[] buf2 = new byte[1020];
			//
			int error=0;
			int SWS=8;									//initial sliding window
			int SNTS=0;
			//int RWS=5;
			int LAR=-1;
			int LFS=-1;
			long[]timer=new long[SWS];
				DatagramPacket []packet1=new DatagramPacket[SWS];
				byte [] []BUFF=new byte[SWS] [];				
				long timeout=1;
				int SN=0;
				String SeqNo="";
				boolean sentAllPackets=false;
				boolean transferComplete=false;
				int endSN=9999;
				int count=0;
				boolean endu=true;
				int ssthresh=64;
				timer=new long[1034];
				packet1=new DatagramPacket[1034];
				BUFF=new byte[1034] [];
				boolean [] packetsent=new boolean[1034];
				int k=0;
				while(k<1034){
					packet1[k]=null;
					packetsent[k]=false;
					k++;
				}
				SWS=1;
				boolean slowStart=true;
				boolean  congestionAvoidance=false;
				long RTT=10;
				long timePassed=System.currentTimeMillis();
				double alfa=0.9;
				int dup=0;
				//
				boolean errFlag=false;			
				int io=0; 
			
			k=0;

			File f = new File(filename);
                        FileInputStream fstream;
                        DataInputStream in;
                        if(f.exists()){
                        fstream = new FileInputStream(filename);
                        in = new DataInputStream(fstream);
                        int dummy = chunkIndex;
                        while(dummy!=0){//updated the piointer for getting the required chunk
                                io = in.read(waste,0,1024*1024);
                                dummy--;
                        }
                        }
                        else{
                                fstream = new FileInputStream(filename+"_chunk_"+chunkIndex+".txt");
                                in = new DataInputStream(fstream);
                        }
			io = in.read(buf2,0,1020);//data
			k=0;
			boolean flagEnd = false;

				while(!transferComplete){



					int i=0;
					while(i<SWS){						//see for timeout of some packet already send
						if(BUFF[i]!=null && System.currentTimeMillis()-timer[i]>timeout){
							if(SWS/2>2)ssthresh=SWS/2;
							else ssthresh=2;
							SWS=1;						//restart slow start
							slowStart=true;
							congestionAvoidance=false;
							byte []Y=new byte[1024];
							int in1=0;
							while(in1<BUFF[i].length){
								Y[in1]=BUFF[i][in1];
								in1++;
							}
							DatagramPacket X=new DatagramPacket(Y,BUFF[i].length,dir,port);
							System.out.println("resend packet : "+getSN(X));

							if(probability())socket.send(X);	//resend packet[i];
							timer[i]=System.currentTimeMillis();//update timer entry;
						}
						i++;
					}
					while(LFS-LAR>=0 && LFS-LAR<SWS){
						if(io!=-1 && k<1028){							
							if(SNTS/1000!=0)SeqNo=""+SNTS;
							else if(SNTS/100!=0)SeqNo="0"+SNTS;
							else if(SNTS/10!=0)SeqNo="00"+SNTS;
							else SeqNo="000"+SNTS;


							copySN(sn,SeqNo);						
							copy(sn,buf2,buf);
							byte[] sendbuf=new byte[1024];
							int in1=0;
							while(in1<1024){
								sendbuf[in1]=buf[in1];
								in1++;
							}
							BUFF[LFS-LAR]=sendbuf;
	              					DatagramPacket packet = new DatagramPacket(buf, io+4,dir,port);
							System.out.println("send packet with sn : "+SNTS);
							if(probability())socket.send(packet);							
							k++;
							timer[LFS-LAR]=System.currentTimeMillis();
							packet1[LFS-LAR]=packet;
							if(k==1028){
								io = in.read(buf4,0,16);
							}
							else{
		        					io = in.read(buf2,0,1020);
							}
						}
						else if(io!=-1 && k==1028){
							if(SNTS/1000!=0)SeqNo=""+SNTS;
							else if(SNTS/100!=0)SeqNo="0"+SNTS;
							else if(SNTS/10!=0)SeqNo="00"+SNTS;
							else SeqNo="000"+SNTS;
							copySN(sn,SeqNo);
							buf5[0]=sn[0];
							buf5[1]=sn[1];
							buf5[2]=sn[2];
							buf5[3]=sn[3];
							int l=4;
							while(l<20){
								buf5[l]=buf4[l-4];
								l++;
							}
							if(io<16){
								DatagramPacket packet = new DatagramPacket(buf5, io+4,dir,port);
		           					if(probability())socket.send(packet);
								//System.out.println("send packet with sn : "+SNTS);
								k++;
								timer[LFS-LAR]=System.currentTimeMillis();
								packet1[LFS-LAR]=packet;
								byte[] sendbuf=new byte[io+4];
								int in1=0;
								while(in1<io+4){
									sendbuf[in1]=buf5[in1];
									in1++;
								}
								BUFF[LFS-LAR]=sendbuf;
							}
							else{
								DatagramPacket packet = new DatagramPacket(buf5, 20,dir,port);
		           					if(probability())socket.send(packet);
								//System.out.println("send packet with sn : "+SNTS);
								k++;
								timer[LFS-LAR]=System.currentTimeMillis();
								packet1[LFS-LAR]=packet;
								byte[] sendbuf=new byte[20];
								int in1=0;
								while(in1<20){
									sendbuf[in1]=buf5[in1];
									in1++;
								}
								BUFF[LFS-LAR]=sendbuf;
							}							
						}
						else{
							
	        					byte[] buf1 = new byte[1024];
							if(SNTS/1000!=0)SeqNo=""+SNTS;
							else if(SNTS/100!=0)SeqNo="0"+SNTS;
							else if(SNTS/10!=0)SeqNo="00"+SNTS;
							else SeqNo="000"+SNTS;
							copySN(buf1,SeqNo);
					buf1[4]='@';
					buf1[5]='#';
					buf1[6]='$';
					buf1[7]='\0';
	        					DatagramPacket packet = new DatagramPacket(buf1,1024, dir,port);
    							if(probability())socket.send(packet);
							//System.out.println("send packet with sn : "+SNTS);
							timer[LFS-LAR]=System.currentTimeMillis();
							packet1[LFS-LAR]=packet;
							byte[] sendbuf=new byte[20];
							int in1=0;
							while(in1<20){
								sendbuf[in1]=buf1[in1];
								in1++;
							}
							BUFF[LFS-LAR]=sendbuf;			
	       						in.close();
							if(endu){
								endu=false;
								endSN=SNTS;
							}
							sentAllPackets=true;
						}
						LFS=LFS+1;
						SNTS=SNTS+1;					
					}
					byte []buf3=new byte[1024];
					DatagramPacket ACK=new DatagramPacket(buf3,1024);
					try{
						socket.receive(ACK);						
					}
					catch(SocketTimeoutException se){						
						errFlag=true;
						error++;
						if(error<1000)continue;
						else break;	
					}
					SN=getSN(ACK);
					//System.out.println("got an ack with sn : "+SN);					
					if(SN==endSN) {
						transferComplete = true;
						//System.out.println("transfer complete");					
						break;
					}
					count=0;
					if(SN >LAR){								//assuming cumulative acking
						RTT=(long)(alfa*(double)RTT+(1.0-alfa)*(double)(System.currentTimeMillis()-timer[SN-LAR-1]));
						//System.out.println("RTT of this packet : "+(int)(System.currentTimeMillis()-timer[SN-LAR-1]));
						//System.out.println("exponential RTT is : "+RTT);
						if(dup>=3){SWS=ssthresh;				//deflating the window that is increased for fastRetransmit
						System.out.println("current SWS is " +SWS);
						}
						else {
							if(slowStart){SWS++;				//increase window size each time you receive an ack for slow start
							System.out.println("current SWS is " +SWS);
							}
							else{							//and for congestion avoidance at RTT
								if(System.currentTimeMillis()-timePassed>RTT){
									SWS++;
									System.out.println("current SWS is " +SWS);
									timePassed=System.currentTimeMillis();
								}
							}
						}
						dup=0;
						if(SWS>ssthresh && !congestionAvoidance){//move to congestion avoidance
							slowStart=false;
							congestionAvoidance=true;
							System.out.println("went into congestion avoidance");
						}
						count=SN-LAR;
					}
					else{
						dup++;
						//drop the ack
					}
					int oldLAR=LAR;
					LAR=LAR+count;
					if(dup>=3){								//fast retransmit inspite of timeout
															//retransmit the lost packet
						byte []Y=new byte[1024];
						int in1=0;
						while(in1<BUFF[0].length){
							Y[in1]=BUFF[0][in1];
							in1++;
						}
						DatagramPacket X=new DatagramPacket(Y,BUFF[0].length,dir,port);
						System.out.println("FastRetransmit packet : "+getSN(X));
						if(probability())socket.send(X);
						timer[0]=System.currentTimeMillis();
						if(dup==3){							//update the window
							if(SWS/2>2)ssthresh=SWS/2;
							else ssthresh=2;
							SWS=SWS+3;						//inflates the window by 3 since 3 dup acks
						}
						else{ SWS=SWS+1;					//for each next inflate the window by 1
							System.out.println("current SWS is " +SWS);
						}
					}
					if(count>0){
															//update packet[] and timer[] i.e. shift the remaining entries
						int j=0;
						//System.out.println("count : "+count);
						while(count<SNTS-oldLAR){
							BUFF[j]=BUFF[count];
							packet1[j]=packet1[count];
							timer[j]=timer[count];
							j++;
							count++;
						}
						while(j<SNTS-oldLAR){
							packet1[j]=null;
							BUFF[j]=null;
							j++;
						}						
						count=0;
					}
					if(transferComplete){
						//System.out.println("transfer complete");
						break;
					}
				}											//max seq has upper bound of  1033 so bytes 0 to 3 of data are reserved
															//for SN since for each chunk we have sliding window implementation
															//and chunk has at max 1029 packets
		       		in.close();
		        	//socket.close();



			in.close();
			socket.close();
			System.out.println("Transfer of chunk No."+chunkIndex+" of file "+filename+" to "+dir.getHostName()+" is complete.");
			Server.free_slot[index] = true;
			
		}
		catch (SocketException ex) {
			ex.printStackTrace();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
		catch (Exception ex) {
			//out.write("exception in server sender thread "+ex);
			ex.printStackTrace();
		}
		//out.write("Completely exiting this thread");
	}

	public int getSN(DatagramPacket P){
        //read first 4 bytes of data and get SN
		byte []st=P.getData();
		byte []SN=new byte[4];
		SN[0]=st[0];
		SN[1]=st[1];
		SN[2]=st[2];
		SN[3]=st[3];
		String SeqN=new String(SN);
		int seq=Integer.parseInt(SeqN);
		return seq;
	}
	public void copyLast(byte[] sn,byte[] buf2,byte[]buf,int io){
		buf[0]=sn[0];
		buf[1]=sn[1];
		buf[2]=sn[2];
		buf[3]=sn[3];
		int i=4;
		while(i<io+4){
			buf[i]=buf2[i-4];
			i++;
		}
	}
	
	public void copySN(byte[] buf,String SN){
		int i=0;
		char d=0;
		while(i<4){
			d=SN.charAt(i);
			switch (d) {
				case '0': buf[i]='0';break;
				case '1': buf[i]='1';break;
				case '2': buf[i]='2';break;
				case '3': buf[i]='3';break;
				case '4': buf[i]='4';break;
				case '5': buf[i]='5';break;
				case '6': buf[i]='6';break;
				case '7': buf[i]='7';break;
				case '8': buf[i]='8';break;
				case '9': buf[i]='9';break;
				default: buf[i]='\0';break;
			}
			i++;
		}
		
		
	}
	public void copy(byte[] sn,byte[] buf2,byte[]buf){
		buf[0]=sn[0];
		buf[1]=sn[1];
		buf[2]=sn[2];
		buf[3]=sn[3];
		int i=4;
		while(i<1024){
			buf[i]=buf2[i-4];
			i++;
		}
	}
	
	public void copy(byte[] buf,String SN){
		int i=0;
		char d=0;
		while(i<4){
			d=SN.charAt(i);
			switch (d) {
				case '0': buf[i]='0';break;
				case '1': buf[i]='1';break;
				case '2': buf[i]='2';break;
				case '3': buf[i]='3';break;
				case '4': buf[i]='4';break;
				case '5': buf[i]='5';break;
				case '6': buf[i]='6';break;
				case '7': buf[i]='7';break;
				case '8': buf[i]='8';break;
				case '9': buf[i]='9';break;
				default: buf[i]='\0';break;
			}
			i++;
		}
	}
	
    public boolean probability(){
		if(Math.random()<Server.send_prob){
			return true;
		}
		else return false;
	}
	
	
	public static String retLastToken(String f, String toSearch){
		try{
			File file = new File(f);
			FileReader in = new FileReader(file);
			BufferedReader br = new BufferedReader(in);
			String  c,d;
			while ((c=br.readLine()) !=null)
			{
				StringTokenizer st = new StringTokenizer(c);
				while ((st.hasMoreTokens())){
					d = st.nextToken();
					if(d.equals(toSearch)){
						while(st.hasMoreTokens()){
							d = st.nextToken();
						}
						return d;
					}
				}
			}
		}
		catch(FileNotFoundException fnf){System.out.println("fille"+fnf);}
		catch( IOException ioe){System.out.println("error"+ioe);}
		return null;
	}

	private static String convertToHex(byte[] data) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < data.length; i++) {
	        	int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				if ((0 <= halfbyte) && (halfbyte <= 9))
				buf.append((char) ('0' + halfbyte));
				else
				buf.append((char) ('a' + (halfbyte - 10)));
				halfbyte = data[i] & 0x0F;
			} while(two_halfs++ < 1);
		}
		return buf.toString();
	}

	public static String SHA1(String text)throws NoSuchAlgorithmException, UnsupportedEncodingException  {
		MessageDigest md;
		md = MessageDigest.getInstance("SHA");
		byte[] sha1hash = new byte[40];
		md.update(text.getBytes(), 0, text.length());
		sha1hash = md.digest();
		return convertToHex(sha1hash);
	}
}

class ShellUtils{

	public static String getStringFromShell(String prompt){
		try{
			System.out.print(prompt);
			return new BufferedReader(new InputStreamReader(System.in)).readLine();		
		}
		catch (IOException e){e.printStackTrace();}
		return null ;
	}

	public static int getIntFromShell(String prompt)
	{
		String line = "" ;
		int num = 0 ;
		while(line.equals(""))
		{
			line = getStringFromShell(prompt);
			try
			{
				num = Integer.parseInt(line);
			}
			catch(NumberFormatException e)
			{
				System.out.println("Error: Invalid number");
				line = "" ;
			}
		}
		return num ;
	}
}

class DateUtils {
	public static final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
	public static String now() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
		return sdf.format(cal.getTime());
	}
}
