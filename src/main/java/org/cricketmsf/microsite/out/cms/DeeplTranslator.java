/*
 * Copyright 2020 Grzegorz Skorupa .
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.cricketmsf.microsite.out.cms;

import org.cricketmsf.microsite.out.cms.TranslatorIface;
import java.util.HashMap;
import org.cricketmsf.Adapter;
import org.cricketmsf.out.OutboundAdapter;

/**
 *
 * @author greg
 */
public class DeeplTranslator extends OutboundAdapter implements TranslatorIface, Adapter {
    
    @Override
    public void loadProperties(HashMap<String, String> properties, String adapterName) {
        super.loadProperties(properties, adapterName);
    }

    @Override
    public Document translate(Document doc, String targetLanguage) throws CmsException {
        throw new CmsException(CmsException.TRANSLATION_NOT_CONFIGURED, "service not configured");
    }
    
}
