/**
  * Code base for other PEPs. 
  * 
  * @author Waleed Kadous
  * @version $Id: LocalMin.java,v 1.1.1.1 2002/06/28 07:36:16 waleed Exp $
  */

package tclass.pep;   
import tclass.*; 
import tclass.util.*; 

public class LocalMin implements PepI {

    static final String name = "lmin"; 
    static final String description = "Finds local minima."; 
    private DomDesc domDesc = null; 
    private int chanIndex; 
    private int windowSize = 3; 
    private int smoothSize = 1; 
    private boolean useRelativeTime = false; 
    private boolean useRelativeHeight = false; 
    private float offset = 0; 

    /** 
     * Gets the name of the PEP. Used by the prototype manager 
     * as a key. 
     *
     * @return A key representing this particular PepPT
     */ 
    public String name(){
	return name; 
    }

        /**
     * Clone the current object. 
     *
     */ 

    public Object clone()
    {
	try {
	    return super.clone(); 
	}
	catch (CloneNotSupportedException e){
	    // Can't happen, or so the java programming book says
	    throw new InternalError(e.toString()); 
	}
    }

    /**
     * Set the domain description
     */ 
    
    public void setDomDesc(DomDesc d){
	domDesc = d; 
    }

    
    /** 
     * Provides a description of the PepI. This description explains
     * what the basic idea of the PEP is (i.e. the sort of shapes it
     * tried to find). It should also explain any potential
     * configuration options that may
     * be used to configure the object, using the configure option. 
     * 
     * @return The description of this class. 
     */ 

    public String description(){ 
	return description; 
    }

    /**
     * Configures this instance so that parameter <i>p</i> has
     * value <i>v</i>. 
     *
     * @param p the parameter to set. 
     * @param v the value of the parameter. 
     * @return true if the operation succeeded. 
     *
     */

    public void setParam(String p, String v) throws InvalidParameterException {
	if(p.equals("window")){
	    try {
		windowSize = Integer.parseInt(v); 
	       
	    }
	    catch(NumberFormatException ne){
		throw new InvalidParameterException(p, v, v + " must be an integer."); 
	    }
	}
	else if(p.equals("channel")){
	    // So they want us to do min on a channel. 
	    // Any channel will do, provided it exists. 
	    chanIndex = domDesc.getChanIndex(v); 
	    if(chanIndex == -1){
		throw new InvalidParameterException(p, v, "Unknown channel " + v); 
	    }

	}
	else if(p.equals("smoothSize")){
	    try {
		 smoothSize = Integer.parseInt(v); 
	    }
	    catch(NumberFormatException ne){
		throw new InvalidParameterException(p, v, v + " must be an integer."); 
	    }
	}
        else if(p.equals("useRelativeTime")){
            if(v.equals("true")){
                useRelativeTime = true; 
            }
            else if(v.equals("false")){
                useRelativeTime = false; 
            }
            else {
                throw new InvalidParameterException(p, v, v + " must be true or false."); 
            }
        }
        else if(p.equals("useRelativeHeight")){
            if(v.equals("true")){
                useRelativeHeight = true; 
            }
            else if(v.equals("false")){
                useRelativeHeight = false; 
            }
            else {
                throw new InvalidParameterException(p, v, v + " must be true or false."); 
            }
        }
	else {
	    throw new InvalidParameterException(p, v, "Unknown parameter"); 
	}
	
    }


    /** 
     *
     * Describes any parameters used by this global extractor,
     * to suit a particular domain. 
     *
     * @return A vector of parameters. 
     */    
    public ParamVec getParamList() {
	ParamVec pv = new ParamVec(); 
	pv.add(new Param("window", "Window width for local minima", "5")); 
	pv.add(new Param("channel", "Channel to work on", "First")); 
        pv.add(new Param("useRelativeTime", "Use time relative to total length", "false")); 
	return pv; 
    }

    /** 
     * Now we get to the complicated stuff.
     *
     * Get a description of the events created by this prototype. 
     * This includes the description of the number of parameters, 
     * the names of the parameters and other info. Note also that
     * this is included free with any EventVec's we return. 
     * 
     */
    public EventDescI getEventDesc(){
	// The first parameter is the value, which is of
	// course the same as the type of the channel. 
       
	
	EventDesc ed = new EventDesc(); 
	ChannelDesc cd = domDesc.getChannel(chanIndex); 
	DataTypeMgr dtm = DataTypeMgr.getInstance();
        	ed.addParam("time", dtm.getClone("continuous")); 
	ed.addParam("value", cd.getDataType()); 
	return (EventDescI) ed; 
    }
    
    float endExtend(float[] input, int index){
	if(index < 0) return input[0]; 
	else if(index >= input.length) return input[input.length-1];
	else return input[index]; 
    }
    
    void smoothDiff(float[] input){
	// Smoothsize must be odd. 
        if(smoothSize==1) return; 
	float windowSum=0; 
        // fill the window sum
	for(int i=-smoothSize/2; i <= smoothSize/2; i++){
	    windowSum += endExtend(input, i); 
	}
	// And now run it along
	for(int i=0; i < input.length; i++){
	    input[i] = windowSum/smoothSize; 
	    windowSum-= endExtend(input, i-smoothSize/2); 
	    windowSum+= endExtend(input, i+smoothSize/2); 
	}
    }
    
    /**
     * The finding function for this PEP. Returns all the events
     * of the form this PEP represents. Returns an EventVecI. 
     * 
     * @param c The channel that we want the finding function to
     * operate on. 
     * @returno A vector of the events of the type extracted by this
     * PEP. 
     */

    public EventVecI findEvents(StreamI s){
	int oldDebugLevel = Debug.getDebugLevel(); 
	Debug.setDebugLevel(Debug.EVERYTHING); 
	EventVec ev = new EventVec(); 
	// Grab our channel. 
	ChannelI c = s.chanAt(chanIndex); 
	int numFrames = c.numFrames(); 
	float[] data = new float[numFrames]; 
        float avg = 0; 
	for(int i=0; i < numFrames; i++){
	    data[i] = c.valAt(i); 
            avg += c.valAt(i); 
	}
        avg /= c.numFrames(); 
        if(useRelativeHeight){
            offset = avg; // HACK!!! This is the hack we use to implement relative height. 
        }
	smoothDiff(data); 
	
	DataTypeI d = domDesc.getChannel(chanIndex).getDataType(); 
	if(numFrames > windowSize){
	    int midWindow = windowSize/2;
	    int limit = numFrames-midWindow-1; 
	    bigLoop: for(int i=midWindow; i < limit; i++){
		// Debug.dp(Debug.EVERYTHING, "Doing " + i); 
		int res = 0; 
		for(int j=i-midWindow+1; j < i; j++){
		    res = d.cmp(data[j], data[j-1]); 
		    if(res != -1)
			continue bigLoop; 
		}
		res = d.cmp(data[i], data[i-1]); 
	if(res != -1)
		    continue bigLoop; 
		for(int j=i+1; j <= i+midWindow ; j++){
		    res = d.cmp(data[j-1], data[j]); 
		    // Debug.dp(Debug.EVERYTHING, "Cmp " + data[j-1] + " " + data[j] + " returns " + res); 
		    if(res != -1)
			continue bigLoop; 
		}
		// Whoa. We got through the test. 
		// So now make the object and add it. 
                if(!useRelativeTime){
                    ev.add(new MinEvent(data[i]-offset, i)); 
                }
                else {
                    ev.add(new MinEvent(data[i]-offset, (float) i/numFrames)); 
                }
		    
	    }
	   
	}
	// Debug.dp(Debug.PROGRESS, "findEvents on " + s.getComment() + " " + domDesc.getChannel(chanIndex).getName() +" returns " + ev); 
	Debug.setDebugLevel(oldDebugLevel); 
	return ev; 
    }
         
}

class MinEvent implements EventI {
    float value; 
    float time; 

    MinEvent(float value, float time){
	this.value = value; 
	this.time = time; 
    }

    public float valOf(int i){
	if(i==0){
	    return time; 
	}
	else if(i==1){
	    return value; 
	}
	else return 0; 
    }
    
    public String toString(){
	return "LocalMin: time = " + time + " val = " + value; 
    }
    
    public float getDuration(){ return 0; }
    public float getMidtime(){ return time; }


}
