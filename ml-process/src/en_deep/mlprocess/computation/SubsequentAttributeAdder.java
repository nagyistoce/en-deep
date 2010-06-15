/*
 *  Copyright (c) 2010 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess.computation;

import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This tries to add attributes for classification subsequently in the given order and then selects the
 * attribute set with the best performance.
 *
 * @author Ondrej Dusek
 */
public class SubsequentAttributeAdder extends WekaSettingTrials {

    /** The name of the "start" parameter */
    private static final String START = "start";
    /** The name of the "end" parameter */
    private static final String END = "end";

    /* DATA */

    /** The file with the attribute order */
    private String attributeOrderFile;
    /** The starting number of attributes */
    private int start;
    /** The maximum number of attributes to try */
    private int end;

    /* METHODS */

    /**
     * This just checks the parameters, inputs and outputs.
     * <p>
     * There are the same parameters as in {@link WekaClassifier} and additional compulsory parameters:
     * </p>
     * <ul>
     * <li><tt>measure</tt> -- the measure selected for comparing the results</li>
     * <li><tt>tempfile</tt> -- the temporary files pattern (must contain one asterisk), without
     * the file extension (automatically adds ".arff" for data and ".txt" for statistics)</li>
     * </ul>
     * <p>
     * There must be exactly three inputs (first of which is the training data, the second the
     * testing data and the third the attribute order file) and two outputs (one is for the
     * classification output and one for the output of the best set of parameters).
     * </p>
     * There are additional voluntary parameters:
     * <ul>
     * <li><tt>start</tt> -- the starting number of attributes</li>
     * <li><tt>end</tt> -- the maximum number of attributes that are to be tried</li>
     * </ul>
     * There is a special parameter reserved for the program (the process ends with this
     * parameter). If the task is run with this parameter, more inputs are allowed.
     * <ul>
     * <li><tt>select_from_evaluations</tt> -- starts the selection from finished evaluations, if it's set
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    public SubsequentAttributeAdder(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (!this.evalMode){
            this.attributeOrderFile = this.input.remove(this.input.size()-1);
        }

        // check voluntary parameters
        try {
            this.start = -1;
            this.end = -1;
            if (this.getParameterVal(START) != null){
                this.start = Integer.parseInt(this.getParameterVal(START));
            }
            if (this.getParameterVal(END) != null){
                this.end = Integer.parseInt(this.getParameterVal(END));
            }
        }
        catch (NumberFormatException e){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameters start and "
                    + "end must be numeric.");
        }

        // check the number of inputs and outputs
        if ((!this.evalMode && input.size() != 2) || (input.size() < 2) || (input.size() % 2 != 0)){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (output.size() != 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
    }


    @Override
    protected Hashtable<String, String> [] prepareParamSets() throws TaskException {

        String [] attribOrder = null;
        Hashtable<String, String>[] paramSets = null;
        int lo, length;

        try {
            attribOrder = readAttributeOrder();
        }
        catch (IOException e){
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, "Error accessing the attribute file.");
        }

        length = attribOrder.length - 1;
        if (this.start != -1){
            length -= this.start;
        }
        if (this.end != -1){
            length -= attribOrder.length - this.end;
        }
        paramSets = new Hashtable[length];
        lo = Math.max(this.start, 1);
        for (int i = lo; i < lo + length; ++i){

            paramSets[i-lo] = this.prepareParamSet(StringUtils.join(attribOrder, lo, i+1, " "));
        }
        return paramSets;
    }


    /**
     * This retrieves the attribute order from the attribute order file.
     * @return the attributes as they appear in the attribute order file
     * @throws IOException
     */
    protected String [] readAttributeOrder() throws IOException {

        RandomAccessFile orderData = new RandomAccessFile(this.attributeOrderFile, "r");
        String line = orderData.readLine();
        String[] orderStr = line.split("\\s+");

        orderData.close();
        return orderStr;
    }

    /**
     * This prepares the parameter set for a classification task with the given list of attributes to use.
     *
     * @param attribList the list of attributes to be used
     * @return the full parameter set for a classification task
     * @todo merge with GreedyAttributeSearch by moving classArg and wekaClass and this to EvalSelector
     */
    private Hashtable<String, String> prepareParamSet(String attribList) {

        Hashtable<String, String> paramSet = new Hashtable<String, String>();

        paramSet.put(CLASS_ARG, this.classArg);
        paramSet.put(WEKA_CLASS, this.wekaClass);
        paramSet.put(TEMPFILE, this.tempFilePattern);
        paramSet.put(WekaClassifier.SELECT_ARGS, attribList);
        paramSet.putAll(this.parameters); // these are just the classifier parameters

        return paramSet;
    }
}
