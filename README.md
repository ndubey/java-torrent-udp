java-torrent-udp
================

Peer to Peer File Sharing/Transfer
----------------
Without using Bit-Torrent technology and its dramatically reduced demands on networking hardware and bandwidth, we could not afford current demands of file transfer. In this project we have created a peer to peer file sharing/transfer program similar to Bit-Torrent. In a Bit-Torrent program instead of a monolithic file being downloaded from a single site/peer the file is broken up into chunks and each chunk is downloaded separately and from possibly different peers and then
assembled into the complete file. One clear advantage is that the time required to complete the download is reduced considerably. 

Project Overview:
----------------
Done in java (Protocol used:UDP) The aim of this project was to create a variation of Bit-torrent. Peers share files among themselves but unlike bit-torrent there is no central tracker peer. A peer fetches all the information about each chunk of the file it wants to download. Although trackers are the only way for peers to find each other, and the only point of coordination at all, We have done all this without any tracker so all peer flood the n/w when any
change has been done since it doesn't know which peer's are online currently. So it broadcasts the message to all users in list. Now any user who is online will receive his message and reply to him that it's done. If some peer are not online then he will not reply still the peer which is broadcasting will try for 3 or 4 times to connect to him since UDP is unreliable. So we have done 3 or 4 broadcast to ensure it that must reaches at least some online peer. Now if some other peer become
online then it is possible that he doesn't have all updated file so he will broadcast to know which peer are online if any peer are online then he will send his update time of files so the other one will compare it with his update time and send him whether his files are old or new. Now he will download the files if he has older version. Since we are comparing the update time so it will also ensure at some extent that if all user are not online at a time and an older update users come
then he will not update his files from him so he has the newer version . A file is made up of consecutive chunks of 1MB (may be except last chunk). Chunks can be located on any peer and the file is downloaded several chunk at a time from possibly different peers and then assembled into a file at the peer who requested the file. Each chunk is obtained using UDP instead of TCP. Since UDP is inherently unreliable, additional measures were undertaken to ensure reliability of each
Data-gram packet sent. The application also implements congestion control mechanisms and rare chunk first. 


Terminology: 
----------------
The application maintains the following files for each Peer. 
-  main.txt - Contains the list of all filenames shared by all the Peers. With each filename, the number of Peers sharing this file is maintained. 
- users.txt - Contains the list of all Peers(both offline and on- line). This is useful for broadcasting messages. 
- shared.txt - This file maintains the list of filenames of the files shared by this user. It also has the total number of chunks of this file and chunk numbers of the chunks of the ﬁle shared by this user. 
- unfinishedDownload.txt - If a particular file coudn't be down- loaded in a particular session(this is possible because the Peers which had the chunk(s) of this file was not up/online ), then those file information is kept in this. It essentially has the information of the chunks which are yet to be downloaded. 
- masterChunk.txt - Contains SHA hash of all the chunks ever uploaded for sharing. When a peer uploads a new file(or some of its chunks) for sharing, this file is updated by appending the SHA hashes of the chunks and broadcasted to all the peers. 


Features: 
----------------
- If a new user wants to join the group of existing peers, only the IP address of an existing Peer is required. it is asked to enter the IP address of one of the existing peers. The newly added user will download all the required files from that IP address. It will now broadcast to all the existing Peers the information of its addition to the group. 
- For a download request the peer is not required to enter the complete filename; only a substring is sufficient. The filenames which have the entered string as substring are printed on screen together with a number. Only that number(matching your choice) needs to be entered in order to download the file. 
- If a file (or some of its chunks) coudn't be downloaded during its first download request(may be because no on- line Peer had that chunk or the receiving Peer itself become disconnected for some reason), its information is stored in unfinishedDown- load.txt. Whenever the Peer exercises option 4, the download is resumed exactly from the state it was left earlier. 
- The chunks are strictly downloaded on rarest chunk ﬁrst basis. This is the safest choice in order to download all the chunks. 
- A Peer has certain number of slots pre-deﬁned for parallel download and upload. The default value is set to four. This means that a Peer can download from four Peers at a time and also serve to four different peers. You can change these values. 
- A chunk downloaded by a Peer is automatically shared and can be downloaded from another Peer on the ﬂy(i.e. even when the downloading of file is in progress). 
- If a peer disconnects for some reason from which some other peer is transfering data, then receiver will acknowledge the user about it and take that chunk from some other peer who is available with that chunk otherwise if there is no alternate then again peer will tell user that he is not able to download all file in this session so put it in unfinished download and will try to download next time (done automatically). 
- We have implemented Congestion control and Congestion Avoidance and also Fast retransmit. So our peers are able to determine the maximum bandwidth that it can use. 



Congestion Control 
----------------
The implementation is similar to what TCP does for congestion control and avoidance. It works like determining the capacity of network so we keep on determining dynamically the capacity of network at any moment. This determination is done on the basis of what is the largest window size of sender at which the network start dropping data so the bottleneck of network any router or switch whose buffer start overﬂowing so we increase the window size exponentially till we
see the first loss. The window size is increased by one after each ack received, this is called slow start but actually it is exponential speed up because we send one packet receive the ack then send two packets receive the acks then send 4 and so on now at first loss detection we know that previously when we have send (current window size /2) packets then it is just fine and no loss has occurred but now at current window size network has went in congestion so the capacity
lie between these two window size we ﬁx our threshold window and again go in slow start. Initally the threshold window is fixed to some large value (we chose 64). This is congestion control. By keeping on doing this we are able to determine the window size at which the network not loose data. Which is when we enter in more than threshold size window by slow start mechanism. Then we enter in Collision Avoidance. In Collision avoidance we increase the window size linearly. Sender is
not aggressive as before flooding the network with twice as much packets each time. Now In-spite of how much ack we receive we will increase the window size by after each RTT. Which implies that it is using the ack as to determine that one of its packet has safely reached the receiver and hence has leaved the network and so it is therefore safe to insert one extra packet without adding congestion, This is collision Avoidance. Initially we see that the sender is aggressive to deter- mine
the optimal window size sending too much data to determine when the loss has occurred but now it is in avoidance zone now it is increasing the window slowly. 


Fast Retransmit 
----------------
Since the receiver is sending ack whenever it gets a packet in-spite it is not the expected packet. So either he drop the packet or put it in his buffer or empty the buffer and write it in the file he will send the ack of last serial number for which he can send the ack. Now when the sender receive three duplicate acks of some packet. HE knows that the next expected packet in the ack was lost, even if the time out has not occurred. The sender then retransmits the lost
packet, and goes back to Slow Start mode. 


Rarest First 
----------------
Selecting pieces to download in a good order is very important for good performance. A poor piece selection algorithm can result in having all the pieces which are currently on offer or, on the flip side, not having any pieces to upload to peers you wish to. Rare pieces are generally only present on one peer, so they would be downloaded slower than pieces which are present on multiple peers for which its possible to download sub-pieces from different places. 
