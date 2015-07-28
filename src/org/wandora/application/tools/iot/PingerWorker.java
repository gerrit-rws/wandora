 /*
 * WANDORA
 * Knowledge Extraction, Management, and Publishing Application
 * http://wandora.org
 * 
 * Copyright (C) 2004-2015 Wandora Team
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 */

package org.wandora.application.tools.iot;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.wandora.application.Wandora;
import org.wandora.application.tools.extractors.ExtractHelper;
import org.wandora.application.tools.iot.PingerPanel.Logger;
import org.wandora.topicmap.TMBox;
import org.wandora.topicmap.Topic;
import org.wandora.topicmap.TopicMap;
import org.wandora.topicmap.TopicMapException;
import org.wandora.utils.DataURL;


/**
 *
 * @author Eero Lehtonen
 */


class PingerWorker {
    
    private static final DateFormat df = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    
    static void run(Topic targetType, Topic sourceType, TopicMap tm, Logger logger, boolean isBinary, File saveFolder){
        run(targetType, sourceType, tm, logger, isBinary);
        String fileName = "iot_" + df.format(new Date()) + ".jtm";
        try {
            tm.exportJTM(saveFolder.getAbsolutePath() + File.separator + fileName);
        } catch (IOException | TopicMapException e) {
            logger.log(e);
        }
    }
    
    
    static void run(Topic targetType, Topic sourceType, TopicMap tm, Logger logger, boolean isBinary) {
        
        logger.log("Initiating run");
        
        List<Topic> validTopics;
        Topic lang;
        
        Unirest.clearDefaultHeaders();
        Unirest.setDefaultHeader("User-Agent", Wandora.USER_AGENT);
        
        try {
            validTopics = getValidTopics(tm, sourceType);
            lang = ExtractHelper.getOrCreateTopic(TMBox.LANGINDEPENDENT_SI, tm);

        } catch (Exception e) {
            logger.log(e);
            return;
        }
        
        for(Topic t : validTopics) {
            try {
                handleTopic(t, targetType, sourceType, lang, isBinary);
            } catch (TopicMapException | UnirestException | IOException e) {
                logger.log(e);
            }
        }
    }
    
    
    private static void handleTopic(Topic t, Topic targetType, Topic sourceType,
            Topic langTopic, boolean isBinary)
            throws TopicMapException, UnirestException, IOException {
        
        String url = t.getData(sourceType, langTopic);
        
        if(url == null) {
            throw new TopicMapException("Invalid URL for topic " + t.getBaseName());
        }
        
        // Check if URL matches any virtual sources
        SourceMapping sourceMapping = SourceMapping.getInstance();
        IoTSource iotSource = sourceMapping.match(url);    
        if(iotSource != null) {
            String data = iotSource.getData(url);
            t.setData(targetType, langTopic, data);
        }
        else if(isBinary) { // Fallback: check if we're fetching binary data
            
            HttpResponse<InputStream> response = Unirest.get(url).asBinary();
            InputStream stream = response.getBody();
            String contentType = response.getHeaders().getFirst("content-type");
            byte[] data = IOUtils.toByteArray(stream);
            DataURL durl = new DataURL(contentType, data);        
            t.setData(targetType, langTopic, durl.toExternalForm());
        } 
        else { // Fallback: fetch as String
            HttpResponse<String> response = Unirest.get(url).asString();
            String data = response.getBody();
            t.setData(targetType, langTopic, data);
        }
    }
    
    
    private static List<Topic> getValidTopics(TopicMap tm, Topic sourceType) throws TopicMapException {
        Iterator<Topic> topics = tm.getTopics();
        List<Topic> validTopics = new ArrayList<>();
        
        while(topics.hasNext()){
            Topic t = topics.next();
            if(t.getDataTypes().contains(sourceType)){
                validTopics.add(t);
            }
        }
        
        return validTopics;
    }

}
