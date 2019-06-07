/*
 * Copyright 2019 Grzegorz Skorupa <g.skorupa at gmail.com>.
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
package org.cricketmsf.out.queue;

import org.cricketmsf.exception.QueueException;
import org.cricketmsf.in.queue.QueueCallbackIface;

/**
 *
 * @author greg
 */
public interface QueueIface {
    
    public static int NOTIFY_NONE = 0;
    public static int NOTIFY_FIRST = 1;
    public static int NOTIFY_ALL = 2;
    
    public void add(String channel, String key, Object value) throws QueueException;
    public void push(String channel, Object value) throws QueueException;
    public Object show(String channel, String key) throws QueueException;
    public Object show(String channel) throws QueueException;
    public Object get(String channel, String key) throws QueueException;
    public Object pop(String channel) throws QueueException;
    public void subscribe(String channel, QueueCallbackIface callback) throws QueueException;
    public void unsubscribe(String channel, QueueCallbackIface callback) throws QueueException;
    public void purge(String channel) throws QueueException;
    public long getSize(String channel) throws QueueException;
    public void setSubscribtionMode(int newMode) throws QueueException;
    public int getSubscribtionMode();
}