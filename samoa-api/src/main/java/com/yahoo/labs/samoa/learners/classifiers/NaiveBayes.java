package com.yahoo.labs.samoa.learners.classifiers;

/*
 * #%L
 * SAMOA
 * %%
 * Copyright (C) 2013 Yahoo! Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import com.yahoo.labs.samoa.moa.classifiers.core.attributeclassobservers.GaussianNumericAttributeClassObserver;
import com.yahoo.labs.samoa.moa.core.AutoExpandVector;

/**
 * Implementation of a non-distributed Naive Bayes classifier.
 * 
 * At the moment, the implementation models all attributes as numeric attributes.
 * 
 * @author Olivier Van Laere (vanlaere yahoo-inc dot com)
 */
public class NaiveBayes implements LocalLearner {

	/**
	 * serialVersionUID for serialization
	 */
	private static final long serialVersionUID = 1325775209672996822L;

	/**
	 * Instance of a logger for use in this class.
	 */
	private static Logger log = LoggerFactory.getLogger(NaiveBayes.class);
	
    /**
     * The actual model. 
     */
    protected AutoExpandVector<AttributeClassObserver> attributeObservers;
    
    /**
     * Class statistics
     */
    protected Map<Integer, Double> classInstances;
    
    /**
     * Retrieve the number of classes currently known to this local model
     * @return the number of classes currently known to this local model
     */
    protected int getNumberOfClasses() {
    	return this.classInstances.keySet().size();
    }
    
    /**
     * Track training instances seen.
     */
    protected long instancesSeen = 0L;
    
    /**
     * Explicit no-arg constructor.
     */
    public NaiveBayes() {
  		// Init the model
  		resetLearning();
    }
    
    /**
     * Create an instance of this LocalLearner implementation.
     */
	@Override
	public LocalLearner create() {
		return new NaiveBayes();
	}
	
    /**
     * Predicts the class memberships for a given instance. If an instance is
     * unclassified, the returned array elements will be all zero.
     *
     * Smoothing is being implemented by the AttributeClassObserver classes. At the
     * moment, the GaussianNumericProbabilityAttributeClassObserver needs no smoothing
     * as it processes continuous variables.
     * 
     * Please note that we transform the scores to log space to avoid underflow, and 
     * we replace the multiplication with addition.
     * 
     * The resulting scores are no longer probabilities, as a mixture of probability
     * densities and probabilities can be used in the computation.
     * 
     * @param inst the instance to be classified
     * @return an array containing the estimated membership scores of the
     * test instance in each class, in log space.
     */
	@Override
	public double[] getVotesForInstance(Instance inst) {
		// Prepare the results array
		double [] votes = new double[getNumberOfClasses()];
		// Over all classes
        for (int classIndex = 0; classIndex < votes.length; classIndex++) {
        	// Get the prior for this class 
        	votes[classIndex] = Math.log(getPrior(classIndex));
        	// Iterate over all the attributes of the instance
            for (int i = 0; i < inst.numAttributes(); i++) {
            	// Skip class attribute itself
            	if (i==inst.classIndex())
            		continue;
            	// Get the observer for the given attribute
                AttributeClassObserver obs = attributeObservers.get(i);
                // Sanity check - observer nor instance is missing
                if ((obs != null) && !inst.isMissing(i)) {
                	double value = obs.probabilityOfAttributeValueGivenClass(inst.value(i), classIndex);
                	votes[classIndex] += Math.log(value);
                }
            }
        }
        return votes;
	}
	
	/**
	 * Compute the prior for the given classIndex.
	 * 
	 * Implemented by maximum likelihood at the moment.
	 * 
	 * @param classIndex Id of the class for which we want to compute the prior.
	 * @return Prior probability for the requested class
	 */
	private double getPrior(int classIndex) {
		// Maximum likelihood
		Double currentCount = this.classInstances.get(classIndex);
		if (currentCount == null)
			return 0;
		else 
			return this.classInstances.get(classIndex) * 1. / this.instancesSeen;
	}
	
	/**
     * Resets this classifier. It must be similar to starting a new classifier
     * from scratch.
     */
	@Override
	public void resetLearning() {
		// Reset priors
        this.instancesSeen = 0L;
		this.classInstances = new HashMap<Integer, Double>();
		// Init the attribute observers
  		this.attributeObservers = new AutoExpandVector<AttributeClassObserver>();        
	}

    /**
     * Trains this classifier incrementally using the given instance.
     *
     * @param inst the instance to be used for training
     */
	@Override
	public void trainOnInstance(Instance inst) {
		
		// Update class statistics with weights
		int classIndex = (int) inst.classValue();
		Double currentValue = this.classInstances.get(classIndex);
		if (currentValue == null)
			currentValue = 0.0;
		this.classInstances.put(classIndex, currentValue + inst.weight());
		
		// Iterate over the attributes of the given instance
        for (int i = 0; i < inst.numAttributes(); i++) {
        	// Skip class attribute
        	if (i == inst.classIndex())
        		continue;
        	// Get the attribute observer for the current attribute
            AttributeClassObserver obs = this.attributeObservers.get(i);
            // Lazy init of observers, if null, instantiate a new one
            if (obs == null) {
            	// FIXME: At this point, we model everything as a numeric attribute
            	obs = new GaussianNumericAttributeClassObserver();
                this.attributeObservers.set(i, obs);
            }
            // FIXME: Sanity check on data values, for now just learn
            // Learn attribute value for given class
        	obs.observeAttributeClass(inst.value(i), (int) inst.classValue(), inst.weight());
        }
        // Count another training instance
        this.instancesSeen++;
	}

	@Override
	public void setDataset(Instances dataset) {
		 // Do nothing
	}
}