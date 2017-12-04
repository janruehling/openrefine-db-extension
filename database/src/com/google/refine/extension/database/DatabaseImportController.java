/*

Copyright 2011, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.extension.database;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectManager;
import com.google.refine.ProjectMetadata;
import com.google.refine.RefineServlet;
import com.google.refine.commands.HttpUtilities;
import com.google.refine.extension.database.model.DatabaseQueryInfo;
import com.google.refine.importing.DefaultImportingController;
import com.google.refine.importing.ImportingController;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingManager;
import com.google.refine.model.Project;
import com.google.refine.util.JSONUtilities;
import com.google.refine.util.ParsingUtilities;


public class DatabaseImportController implements ImportingController {
    
    final static Logger logger = LoggerFactory.getLogger("DatabaseImportController");

    protected RefineServlet servlet;

    public static int DEFAULT_PREVIEW_LIMIT = 100;
    
    public static String OPTIONS_KEY = "options";
    
    @Override
    public void init(RefineServlet servlet) {
        this.servlet = servlet;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        HttpUtilities.respond(response, "error", "GET not implemented");
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        logger.info("DatabaseImportController::doPost::databaseName::{}", request.getParameter("databaseName"));

        response.setCharacterEncoding("UTF-8");
        Properties parameters = ParsingUtilities.parseUrlParameters(request);
        
        String subCommand = parameters.getProperty("subCommand");
        
        logger.info("DatabaseImportController::doPost::subCommand::{}", subCommand);
        
        if ("initialize-parser-ui".equals(subCommand)) {
            doInitializeParserUI(request, response, parameters);
        } else if ("parse-preview".equals(subCommand)) {
            try {
                doParsePreview(request, response, parameters);
            } catch (DatabaseServiceException e) {
                logger.error("DatabaseImportController::doPost::DatabaseServiceException::{}", e);
                HttpUtilities.respond(response, "error", e.getMessage());
            }
        } else if ("create-project".equals(subCommand)) {
            doCreateProject(request, response, parameters);
        } else {
            HttpUtilities.respond(response, "error", "No such sub command");
        }

    }
 
    /**
     * 
     * @param request
     * @param response
     * @param parameters
     * @throws ServletException
     * @throws IOException
     */
    private void doInitializeParserUI(HttpServletRequest request, HttpServletResponse response, Properties parameters)
            throws ServletException, IOException {
        logger.info("::doInitializeParserUI::");

        JSONObject result = new JSONObject();
        JSONObject options = new JSONObject();
        JSONUtilities.safePut(result, "status", "ok");
        JSONUtilities.safePut(result, OPTIONS_KEY, options);

        JSONUtilities.safePut(options, "skipDataLines", 0); 
        JSONUtilities.safePut(options, "storeBlankRows", true);
        JSONUtilities.safePut(options, "storeBlankCellsAsNulls", true);

        logger.info("doInitializeParserUI:::{}", result.toString());
        HttpUtilities.respond(response, result.toString());

    }


    /**
     * 
     * @param request
     * @param response
     * @param parameters
     * @throws ServletException
     * @throws IOException
     * @throws DatabaseServiceException 
     */
    private void doParsePreview(
            HttpServletRequest request, HttpServletResponse response, Properties parameters)
                throws ServletException, IOException, DatabaseServiceException {
            logger.info("DatabaseImportController::doParsePreview::JobID::{}", parameters.getProperty("jobID"));
          
            long jobID = Long.parseLong(parameters.getProperty("jobID"));
            ImportingJob job = ImportingManager.getJob(jobID);
            if (job == null) {
                HttpUtilities.respond(response, "error", "No such import job");
                return;
            }
          
            DatabaseQueryInfo databaseQueryInfo = getQueryInfo(request);
            if(databaseQueryInfo == null) {
                HttpUtilities.respond(response, "error", "Invalid or missing Query Info");
            }
            
            job.updating = true;
            try {
                JSONObject optionObj = ParsingUtilities.evaluateJsonStringToObject(
                    request.getParameter("options"));
                
                List<Exception> exceptions = new LinkedList<Exception>();
                
                job.prepareNewProject();
                
                DatabaseImporter.parse(
                    databaseQueryInfo,
                    job.project,
                    job.metadata,
                    job,
                    DEFAULT_PREVIEW_LIMIT ,
                    optionObj,
                    exceptions
                );
                
                Writer w = response.getWriter();
                JSONWriter writer = new JSONWriter(w);
                try {
                    writer.object();
                    if (exceptions.size() == 0) {
                        job.project.update(); // update all internal models, indexes, caches, etc.
                        writer.key("status"); writer.value("ok");
                    } else {
                        writer.key("status"); writer.value("error");
                        writer.key("errors");
                        writer.array();
                        DefaultImportingController.writeErrors(writer, exceptions);
                        writer.endArray();
                    }
                    writer.endObject();
                } catch (JSONException e) {
                    throw new ServletException(e);
                } finally {
                    w.flush();
                    w.close();
                }

            } catch (JSONException e) {
                throw new ServletException(e);
            } finally {
                job.touch();
                job.updating = false;
            }
        }

  

    /**
     * 
     * @param request
     * @param response
     * @param parameters
     */
    private void doCreateProject(HttpServletRequest request, HttpServletResponse response, Properties parameters)
            throws ServletException, IOException{
            logger.info("DatabaseImportController::doCreateProject:::{}", parameters.getProperty("jobID"));
          
            
            long jobID = Long.parseLong(parameters.getProperty("jobID"));
            final ImportingJob job = ImportingManager.getJob(jobID);
            if (job == null) {
                HttpUtilities.respond(response, "error", "No such import job");
                return;
            }
            
            final DatabaseQueryInfo databaseQueryInfo = getQueryInfo(request);
            if(databaseQueryInfo == null) {
                HttpUtilities.respond(response, "error", "Invalid or missing Query Info");
            }
            
            job.updating = true;
            try {
                final JSONObject optionObj = ParsingUtilities.evaluateJsonStringToObject(
                    request.getParameter("options"));
                
                final List<Exception> exceptions = new LinkedList<Exception>();
                
                job.setState("creating-project");
                
                final Project project = new Project();
              
                new Thread() {
                    @Override
                    public void run() {
                        ProjectMetadata pm = new ProjectMetadata();
                        pm.setName(JSONUtilities.getString(optionObj, "projectName", "Untitled"));
                        pm.setEncoding(JSONUtilities.getString(optionObj, "encoding", "UTF-8"));
                    
                        
                        try {
                            DatabaseImporter.parse(
                                databaseQueryInfo,
                                project,
                                pm,
                                job,
                                -1,
                                optionObj,
                                exceptions
                            );
                        } catch (DatabaseServiceException e) {
                            logger.info("DatabaseImportController::doCreateProject:::run{}", e);
                           // throw new RuntimeException("DatabaseServiceException::", e);
                        }
                      
                        if (!job.canceled) {
                            if (exceptions.size() > 0) {
                                //logger.info("DatabaseImportController::doCreateProject:::run::exceptions :{}", exceptions);
                                job.setError(exceptions);
                            } else {
                                project.update(); // update all internal models, indexes, caches, etc.
                                
                                ProjectManager.singleton.registerProject(project, pm);
                                
                                job.setState("created-project");
                                job.setProjectID(project.id);
                               // logger.info("DatabaseImportController::doCreateProject:::run::projectID :{}", project.id);
                            }
                            
                            job.touch();
                            job.updating = false;
                        }
                    }
                }.start();
                
                HttpUtilities.respond(response, "ok", "done");
            } catch (JSONException e) {
                throw new ServletException(e);
            }
        }
    
    /**
     * 
     * @param request
     * @return
     */
    private DatabaseQueryInfo getQueryInfo(HttpServletRequest request) {
        DatabaseConfiguration jdbcConfig = new DatabaseConfiguration();
        jdbcConfig.setConnectionName(request.getParameter("connectionName"));
        jdbcConfig.setDatabaseType(request.getParameter("databaseType"));
        jdbcConfig.setDatabaseHost(request.getParameter("databaseServer"));
        jdbcConfig.setDatabasePort(Integer.parseInt(request.getParameter("databasePort")));
        jdbcConfig.setDatabaseUser(request.getParameter("databaseUser"));
        jdbcConfig.setDatabasePassword(request.getParameter("databasePassword"));
        jdbcConfig.setDatabaseName(request.getParameter("initialDatabase"));
        jdbcConfig.setDatabaseSchema(request.getParameter("initialSchema"));
        
        String query = request.getParameter("query");
        
        if (jdbcConfig.getDatabaseHost() == null || jdbcConfig.getDatabaseName() == null
                || jdbcConfig.getDatabasePassword() == null || jdbcConfig.getDatabaseType() == null
                || jdbcConfig.getDatabaseUser() == null || query == null) {
            
            logger.info("Missing Database Configuration::{}", jdbcConfig);
            return null;
        }
        
        return new DatabaseQueryInfo(jdbcConfig, query);
    }
}
