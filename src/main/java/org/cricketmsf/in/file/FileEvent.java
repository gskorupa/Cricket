/*
 * Copyright 2020 Grzegorz Skorupa .
 *
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
 */
package org.cricketmsf.in.file;

import org.cricketmsf.event.*;

/**
 * To be removed
 * @author greg
 */
@org.cricketmsf.livingdoc.design.BoundedContext(name="Content Management")
@org.cricketmsf.livingdoc.design.Event()
public class FileEvent extends Event {

    public FileEvent(byte[] content) {
        super();
        setData(content);
    }
    
}
