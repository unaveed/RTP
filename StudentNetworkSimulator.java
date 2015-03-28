import java.util.Stack;

public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(int entity, String dataSent)
     *       Passes "dataSent" up to layer 5 from "entity" [A or B]
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          create a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    private int mSequenceNumber = 0;                // The sequence number of original packet, either 1 or 0
    private int mExpectedSequenceNumber = 0;        // The sequence number the receiver expects to recieve
    private int mLastACKSequence = -1;              // The last sequence that the receiver gave an ACK
    private boolean mMessageInTransit = false;      // Prevents or allows aOutput to send messages
    private boolean mRetrieveFromCache = false;     // Determines whether aOutput fetches from cache or layer5
    private Packet mLastPacketSent = null;          // A copy of the last message sent by aOutput
    private Stack<Message> mMessageCache =          // Cache that stores messages which have not received ACK
                        new Stack<Message>();

    // Variables used for gathering statistics
    private int mPacketsTransmitted = 0;            // How many packets sent by aOutput
    private int mNumberOfACK = 0;                   // Packets that received an ACK
    private int mRetransmissions = 0;               // Packets that have been re-transmitted
    private int mCorruptPacketsReceived = 0;        // Corrupt packets received
    private int mTotalRTT = 0;                      // A sum of all RTTs
    private int mRTTCount = 0;                      // Number of RTTs to calculate for average
    private double mTimeSent = 0.0;                 // Keeps track of when the message was sent


    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   long seed)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
    }

    /**
     * Creates checksum by adding the sequence, ack, and each
     * character of the message.
     */
    private int createChecksum(int sequence, int ack, String message)
    {
        int checksum = 0;
        checksum+= sequence;
        checksum+= ack;

        for(int i = 0; i < message.length(); i++)
            checksum+= message.charAt(i);

        return checksum;
    }

    /**
     * Determines whether the mUnsentMessages checksum is the same as
     * the expected checksum.
     */
    private boolean isPacketCorrupt(Packet packet)
    {
        int checksum = createChecksum(packet.getSeqnum(),
                packet.getAcknum(),
                packet.getPayload());

        return checksum != packet.getChecksum();
    }

    /**
     * Generate the next number in the sequence for either A or B
     */
    private void nextSequenceNumber(int side)
    {
        if (side == A)
            mSequenceNumber = (mSequenceNumber + 1) % 2;
        else
            mExpectedSequenceNumber = (mExpectedSequenceNumber + 1) % 2;
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {
        System.out.println("Incoming message at aOutput: " + message.getData());

            if(!mMessageInTransit)
            {
                String data;

                // There are unsent messages, handle them first
                if(mRetrieveFromCache)
                {
                    data = mMessageCache.pop().getData();
                    mRetrieveFromCache = false;
                }
                else
                {
                    data = message.getData();
                }


                int checksum = createChecksum(mSequenceNumber, 1, data);

                // Create packet and send it to side B
                Packet packet = new Packet(mSequenceNumber, 1, checksum, data);
                toLayer3(A, packet);

                mTimeSent = getTime();
                startTimer(A, 20.0);
                System.out.println("Time when sent aOutput: " + getTime());

                // Update the last sent packet
                mLastPacketSent = packet;
                mMessageCache.push(new Message(data));

                // Update state and statistics counter
                mPacketsTransmitted++;
                mMessageInTransit = true;
                System.out.println("aOutput packet: " + packet.toString() + "\n");
            }

        System.out.println("\n--------- Statistics --------\n" +
                           "Number of packets transmitted: " + mPacketsTransmitted + "\n" +
                           "Number of re-transmissions: " + mRetransmissions + "\n" +
                           "Number of ACK packets: " + mNumberOfACK + "\n" +
                           "Number of corrupt packets: " + mCorruptPacketsReceived + "\n");
    }

    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        System.out.println("aInput received packet: " + packet.toString());
        System.out.println("Time when received aInput: " + getTime());

        mTotalRTT += getTime() - mTimeSent;
        mRTTCount++;

        mMessageInTransit = false;
        boolean retransmission = mLastPacketSent.getSeqnum() != packet.getSeqnum();

        // Let timer expire for corrupt packets
        if (isPacketCorrupt(packet))
        {
            mRetrieveFromCache = true;
            mCorruptPacketsReceived++;
            System.out.println("aInput found corrupt packet, resending.\n");
        }
        // Let timer expire for re-transmitted packets and remove message from cache
        else if (retransmission)
        {
            mMessageCache.pop();
            nextSequenceNumber(A);
            System.out.println("aInput found re-transmission, let timer expire.\n");
        }
        // Handle packets that are ACK
        else
        {
            stopTimer(A);
            mNumberOfACK++;
            System.out.println(mPacketsTransmitted + " aInput packet was ACK, stopping timer.\n");

            // Remove message since it has been received by side B
            mMessageCache.pop();
            nextSequenceNumber(A);
            System.out.println("aInput sequence incremented now: " + mSequenceNumber);
        }
    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of mUnsentMessages. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {
        toLayer3(A, mLastPacketSent);
        startTimer(A, 20);
        mTimeSent = getTime();
        mPacketsTransmitted++;
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        System.out.println("Time when arrived bInput: " + getTime());
        System.out.println("bInput packet: " + packet.toString());

        String dummyData = "hello";
        int checksum;
        Packet responsePacket;

        boolean corrupt = isPacketCorrupt(packet);
        boolean retransmission = packet.getSeqnum() != mExpectedSequenceNumber;

        if(corrupt || retransmission)
        {
            checksum = createChecksum(mLastACKSequence, 1, dummyData);
            responsePacket = new Packet(mLastACKSequence, 1, checksum, dummyData);

            if(corrupt)
            {
                mCorruptPacketsReceived++;
                System.out.println("bInput detected corrupt packet, sending sequence of lastACK\n");
            }
            else
            {
                mRetransmissions++;
                System.out.println("bInput received retransmitted packet, sending sequence of lastACK\n");
            }
        }
        else
        {
            // Data is good, send it up
            toLayer5(B, packet.getPayload());

            // Create ACK packet
            checksum = createChecksum(mExpectedSequenceNumber, 1, dummyData);
            responsePacket = new Packet(mExpectedSequenceNumber, 1, checksum, dummyData);

            // Move the expectedSequence forward
            mLastACKSequence = mExpectedSequenceNumber;
            nextSequenceNumber(B);
            System.out.println("bInput, incremented nextSequence before: "
                    + mLastACKSequence + "and now " + mExpectedSequenceNumber);
            System.out.println("bInput packet is good, sending ACK\n");
        }

        toLayer3(B, responsePacket);
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
    }
}
