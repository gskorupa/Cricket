/*
 * Copyright 2016 Grzegorz Skorupa .
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
package org.cricketmsf.out.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.cricketmsf.RequestObject;
import org.cricketmsf.out.db.KeyValueCacheAdapterIface;
import org.cricketmsf.out.db.KeyValueDBIface;
import org.cricketmsf.api.ResultIface;

/**
 *
 * @author greg
 */
public interface FileReaderAdapterIface {
    
    public byte[] readFile(File file) throws FileNotFoundException, IOException;
    public byte[] readFile(String filePath) throws FileNotFoundException, IOException;
    public String getFilePath(RequestObject request);
    public String getFileExt(String filePath);
    public byte[] getFileBytes(File file, String filePath);
    public ResultIface getFile(RequestObject request, KeyValueDBIface cache, String tableName);
    public String getRootPath();
}
