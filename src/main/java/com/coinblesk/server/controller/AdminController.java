/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.controller;

import com.coinblesk.server.utils.ApiVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.servlet.ServletContext;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Thomas Bocek
 */
@RestController
@RequestMapping(value = {"/admin", "/a"})
@ApiVersion({"v1", ""})
public class AdminController {

    private static org.slf4j.Logger LOG = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    ServletContext context;

    @RequestMapping(value = {"/info", "/i"}, method = RequestMethod.GET)
    @ResponseBody
    public String info() {
        LOG.debug("Info called");
        InputStream inputStream = context.getResourceAsStream("/META-INF/MANIFEST.MF");
        try {
            Properties prop = new Properties();
            prop.load(inputStream);
            List<String> keys = new ArrayList<>(prop.stringPropertyNames());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder();
            for (String key : keys) {
                sb.append(key).append(":[");
                sb.append(prop.get(key)).append("]  ");
            }
            return sb.toString().trim();
        } catch (IOException ex) {
            return "no manifest found";
        }
    }
}
