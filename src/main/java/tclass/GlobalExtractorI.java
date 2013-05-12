/**
  * A class for each extractor. 
  *
  * 
  * @author Waleed Kadous
  * @version $Id: GlobalExtractorI.java,v 1.1.1.1 2002/06/28 07:36:16 waleed Exp $
  */

package tclass;   

public interface GlobalExtractorI extends Cloneable {

    /**
     * Gets the base name of this global extractor
     *
     * @return the Extractor's name
     */ 
    
    public String name(); 


    /**
     * Gets the datatype of the current object (which controls things like
     * the distance metric used by the learner). 
     *
     */
    
    public DataTypeI getDataType(); 

    /**
     * Set the domain description that this object will use to interpret 
     * data. 
     *
     */ 
    
    public void setDomDesc(DomDesc d); 

    /**
     * Provides a description for the Global Extractor. It explains the 
     * basic features the Extractor is looking for. 
     *
     * @return A simple description
     * 
     */


    public String description(); 

    /** 
     *
     * Describes any parameters used by this global extractor,
     * to suit a particular domain. 
     *
     * @return A vector of parameters. 
     */
    
    public ParamVec getParamList(); 
    
    /**
     * Configures this particular extractor so that parameter <i>p</i> 
     * has value <i>v</i>.
     *
     * @param p The parameter to set.
     * @param v The value of the parameter. 
     * @return True if the setting succeeded; false otherwise. 
     */
    
    public void setParam(String p, String v) throws InvalidParameterException; 

    /**
     * Gets the feature that this global is supposed to extract. For
     * now, we assume that global extractors return a double. 
     * 
     * @param s the stream we want to extract the global feature 
     * from. 
     * @return The global feature's value. 
     *
     */

    public float extract(StreamI s); 


    public Object clone(); 

}
