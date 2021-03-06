package org.redhat.bpm.service;

import org.kie.server.api.model.definition.QueryFilterSpec;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.TaskInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.api.util.QueryFilterSpecBuilder;
import org.kie.server.client.*;
import org.redhat.bpm.model.TaskDetail;
import org.redhat.bpm.model.TaskDetailWithVariable;
import org.redhat.bpm.model.TaskDetailWithVariableCustom;
import org.redhat.bpm.query.AdvancedQueryFactory;
import org.redhat.bpm.util.BPMN;

import java.util.*;
import java.util.stream.Collectors;

import static org.kie.server.client.QueryServicesClient.*;
import static org.redhat.bpm.query.AdvancedQueryFactory.*;

public abstract class KieService {

    private static final String[] POT_OWNED_STATUS = {"Created", "Ready", "Reserved", "InProgress", "Suspended"};
    public static final String[] OPEN_STATUS = { "Created", "Ready", "Reserved", "InProgress", "Suspended" };
    private static final String TASK_ID = "taskid";
    private static final String PROCESS_INSTANCE_ID = "processinstanceid";
    private static final int ARBITRARY_LONG_VALUE = 10000;
    private static final List<String> TASK_OPEN_STATUS = Arrays.asList(OPEN_STATUS);

    protected KieServicesConfiguration config;

    protected KieServicesClient client;

    public void startConverasation() {
        client = KieServicesFactory.newKieServicesClient(config);
    }

    public void endConversation() {
        client.completeConversation();
    }

    public void registerAdvancedQueries() {
        AdvancedQueryFactory factory = new AdvancedQueryFactory();
        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);
        factory.getDefinitions().forEach(queryDefinition -> {
            queryService.replaceQuery(queryDefinition);
        });

    }

    public List<ProcessInstance> findProcessInstancesWithVariables(String processDefinitionId, Integer page, Integer pageSize) {
        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);

        QueryFilterSpec queryFilterSpec = new QueryFilterSpecBuilder()
                .equalsTo("processid", processDefinitionId)
                .get();

        List<ProcessInstance> query = queryService.query(FIND_PROCESS_INSTANCES_WITH_VARIABLES,
                QUERY_MAP_PI_WITH_VARS, queryFilterSpec, page, pageSize, ProcessInstance.class);

        return query;
    }


    public List<TaskDetailWithVariableCustom>tasksByVariablesAndParamsWithVariablesAndParams(
            Map<String, List<String>> paramsMap, Map<String, List<String>> variablesMap,
            boolean asc) {

        HashMap<String, Object> parameters = new HashMap<>();

        parameters.put("status", Arrays.asList(OPEN_STATUS));

        if (paramsMap != null)
            parameters.put("paramsMap", paramsMap);

        if (variablesMap != null)
            parameters.put("variablesMap", variablesMap);

        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);

        List<TaskInstance> taskWithDuplicates = queryService.query(TASKS_BY_VARIABLES_AND_PARAMS, QUERY_MAP_TASK,
                "tasksByVariablesAndParamsFilter", parameters, 0, ARBITRARY_LONG_VALUE, TaskInstance.class);
        List<Long> ids = taskWithDuplicates.stream().map(taskInstance -> taskInstance.getId()).distinct()
                .collect(Collectors.toList());

        List<TaskInstance> tasksWithParams = findTasksWithParameters(queryService, ids, asc);
        List<TaskDetail> taskDetails = kieTaskDetailsToCommon(tasksWithParams);

        List<Long> processIds = tasksWithParams.stream().map(taskInstance -> taskInstance.getProcessInstanceId())
                .distinct().collect(Collectors.toList());
        List<ProcessInstance> processInstances = findProcessInstancesWithVariables(queryService, processIds);
        Map<Long, Map<String, Object>> processInstanceVariables = processInstances.stream()
                .collect(Collectors.toMap(ProcessInstance::getId, ProcessInstance::getVariables));

        List<TaskDetailWithVariableCustom> taskDetailWithVariableCustoms = taskDetails.stream()
                .map(taskInstance -> new TaskDetailWithVariableCustom(taskInstance,
                        processInstanceVariables.get(taskInstance.getProcessInstanceId())))
                .collect(Collectors.toList());

       return taskDetailWithVariableCustoms;


    }


    public List<Long> potOwnedTasksByVariablesAndTaskParamsInOr(String user, List<String> groups, Map<String, List<String>> paramsMap, Map<String, List<String>> variablesMap) {

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("user", user);
        parameters.put("groups", groups);

        parameters.put("status", Arrays.asList(POT_OWNED_STATUS));

        if (paramsMap != null) {
            parameters.put("paramsMap", paramsMap);
        }

        if (variablesMap != null) {
            parameters.put("variablesMap", variablesMap);
        }

        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);

        List<TaskInstance> taskWithDuplicates = queryService.query(POT_OWNED_TASKS_BY_VARIABLES_AND_PARAMS, QUERY_MAP_TASK, "potOwnedTasksByVariablesAndParamsFilter", parameters, 0, ARBITRARY_LONG_VALUE, TaskInstance.class);

        List<Long> ids = taskWithDuplicates.stream().map(taskInstance -> taskInstance.getId()).distinct().collect(Collectors.toList());

        return ids;

    }

    public List<TaskDetailWithVariable> potOwnedTasksByVariablesWithVariablesAndParamsInAnd(
            String user,
            List<String> groups,
            Map<String, List<String>> variablesMap) {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("user", user);
        parameters.put("groups", groups);
        parameters.put("status", Arrays.asList(POT_OWNED_STATUS));

        if(variablesMap == null || variablesMap.isEmpty())
            throw new IllegalStateException("At least one variable must be provided");

        List<Long> taskIds = potOwnedTasksByVariablesAndParams(user, groups, null, null);
        if(taskIds.isEmpty())
            return null;
        List<TaskInstance> tasksWithParams = findTasksWithParameters(taskIds, true);

        List<Long> processIds = tasksWithParams.stream().map(taskInstance -> taskInstance.getProcessInstanceId()).distinct().collect(Collectors.toList());
        List<ProcessInstance> processInstances = findProcessInstancesWithVariables(processIds);
        Map<Long, Map<String, Object>> processInstanceVariables = processInstances.stream().collect(Collectors.toMap(ProcessInstance::getId, ProcessInstance::getVariables));
        List<TaskDetailWithVariable> taskInstanceWithVariables = tasksWithParams.stream()
                .map(taskInstance -> new TaskDetailWithVariable(taskInstance, processInstanceVariables.get(taskInstance.getProcessInstanceId())))
                .collect(Collectors.toList());

        List<TaskDetailWithVariable> result = new ArrayList<>();
        if(taskInstanceWithVariables != null && !taskInstanceWithVariables.isEmpty()) {
            for(TaskDetailWithVariable taskDetailWithVariable: taskInstanceWithVariables) {
                boolean toAdd = true;
                //check if all keys are present
                Set<String> variablesKeys = variablesMap.keySet();
                Set<String> processVariablesKeys = taskDetailWithVariable.getProcessInstanceVariables().keySet();
                if(processVariablesKeys.containsAll(variablesKeys)) {
                    //iterate variablesKeys
                    for(String variableKey: variablesKeys) {
                        Object o1 = taskDetailWithVariable.getProcessInstanceVariables().get(variableKey);
                        if (!variablesMap.get(variableKey).contains(o1)) {
                            toAdd = false;
                            break;
                        }
                    }
                    if(toAdd)
                        result.add(taskDetailWithVariable);
                }
            }
            return result;

        } else
            return null;
    }

    public List<Long> tasksByNamesAndVariables(List<String> names, Map<String, List<String>> variablesMap) {

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("names", names);

        parameters.put("status", Arrays.asList(OPEN_STATUS));

        if (variablesMap != null)
            parameters.put("variablesMap", variablesMap);

        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);

        List<TaskInstance> taskWithDuplicates = queryService.query(TASKS_BY_NAMES_VARIABLES_AND_PARAMS,
                QUERY_MAP_TASK, "tasksByNamesAndVariablesFilter", parameters, 0, ARBITRARY_LONG_VALUE,
                TaskInstance.class);

        List<Long> ids = taskWithDuplicates.stream().map(taskInstance -> taskInstance.getId()).distinct()
                .collect(Collectors.toList());

        return ids;

    }

    public List<Long> tasksByGroupsAndVariablesAndParams(
            List<String> groups,
            Map<String, List<String>> paramsMap,
            Map<String, List<String>> variablesMap,
            List<String> status) {

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("groups", groups);
        parameters.put("status", status == null || status.isEmpty()? TASK_OPEN_STATUS: status);

        if (paramsMap != null)
            parameters.put("paramsMap", paramsMap);

        if (variablesMap != null)
            parameters.put("variablesMap", variablesMap);

        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);

        List<TaskInstance> taskWithDuplicates = queryService.query(TASKS_BY_GROUPS_AND_VARIABLES_AND_PARAMS,
                QUERY_MAP_TASK, TASK_BY_GROUPS_AND_VARIABLES_AND_PARAMS_FILTER, parameters, 0, ARBITRARY_LONG_VALUE,
                TaskInstance.class);

        List<Long> ids = taskWithDuplicates.stream().map(taskInstance -> taskInstance.getId()).distinct()
                .collect(Collectors.toList());

        return ids;

    }

    public List<Long> tasksByGroupsAndVariablesAndParamsNotActualOwner(
            List<String> groups,
            Map<String, List<String>> paramsMap,
            Map<String, List<String>> variablesMap,
            List<String> status,
            String actualOwner) {

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("groups", groups);
        parameters.put("status", status == null || status.isEmpty()? TASK_OPEN_STATUS: status);
        parameters.put("actualOwner", actualOwner);

        if (paramsMap != null)
            parameters.put("paramsMap", paramsMap);

        if (variablesMap != null)
            parameters.put("variablesMap", variablesMap);

        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);

        List<TaskInstance> taskWithDuplicates = queryService.query(TASKS_BY_GROUPS_AND_VARIABLES_AND_PARAMS,
                QUERY_MAP_TASK, TASK_BY_GROUPS_AND_VARIABLES_AND_PARAMS_NOT_ACTUALOWNER_FILTER, parameters, 0, ARBITRARY_LONG_VALUE,
                TaskInstance.class);

        List<Long> ids = taskWithDuplicates.stream().map(taskInstance -> taskInstance.getId()).distinct()
                .collect(Collectors.toList());


        return ids;

    }

    public List<TaskDetailWithVariable> tasksByVariablesAndNamesWithVariablesAndParamsInAnd(
            List<String> taskNames,
            Map<String, List<String>> variablesMap) {

        if(taskNames == null || taskNames.isEmpty())
            throw new IllegalStateException("At least one taskName must be provided");

        if(variablesMap == null || variablesMap.isEmpty())
            throw new IllegalStateException("At least one variable must be provided");

        List<Long> taskIds = findTasksByNameWithParameters(taskNames);
        if(taskIds.isEmpty())
            return null;
        List<TaskInstance> tasksWithParams = findTasksWithParameters(taskIds, true);

        List<Long> processIds = tasksWithParams.stream().map(taskInstance -> taskInstance.getProcessInstanceId()).distinct().collect(Collectors.toList());
        List<ProcessInstance> processInstances = findProcessInstancesWithVariables(processIds);
        Map<Long, Map<String, Object>> processInstanceVariables = processInstances.stream().collect(Collectors.toMap(ProcessInstance::getId, ProcessInstance::getVariables));
        List<TaskDetailWithVariable> taskInstanceWithVariables = tasksWithParams.stream()
                .map(taskInstance -> new TaskDetailWithVariable(taskInstance, processInstanceVariables.get(taskInstance.getProcessInstanceId())))
                .collect(Collectors.toList());

        List<TaskDetailWithVariable> result = new ArrayList<>();
        if(taskInstanceWithVariables != null && !taskInstanceWithVariables.isEmpty()) {
            for(TaskDetailWithVariable taskDetailWithVariable: taskInstanceWithVariables) {
                boolean toAdd = true;
                //check if all keys are present
                Set<String> variablesKeys = variablesMap.keySet();
                Set<String> processVariablesKeys = taskDetailWithVariable.getProcessInstanceVariables().keySet();
                if(processVariablesKeys.containsAll(variablesKeys)) {
                    //iterate variablesKeys
                    for(String variableKey: variablesKeys) {
                        Object o1 = taskDetailWithVariable.getProcessInstanceVariables().get(variableKey);
                        if (!variablesMap.get(variableKey).contains(o1)) {
                            toAdd = false;
                            break;
                        }
                    }
                    if(toAdd)
                        result.add(taskDetailWithVariable);
                }
            }
            return result;

        } else
            return null;
    }

    public List<Long> potOwnedTasksByVariablesAndTaskParamsInAnd(
            String user,
            List<String> groups,
            Map<String, List<String>> paramsMap,
            Map<String, List<String>> variablesMap) {

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("user", user);
        parameters.put("groups", groups);
        parameters.put("status", Arrays.asList(OPEN_STATUS));

        //evaluate paramsMap composition
        if(paramsMap != null && !paramsMap.isEmpty() && paramsMap.size() > 5)
            throw new IllegalStateException("Max 5 paramsMap are allowed!");

        List<String> keys = new ArrayList<>(paramsMap.keySet());
        String keyToCheck = null;
        String keyToExclude = null;
        String keyToQuery = null;
        String keyToExcludeWithoutCheck = null;

        int excludedCount = 0;
        for(String key: keys) {
            if (key.matches(".*-excluded")) {
                keyToExclude = key;
                excludedCount++;
            }
        }

        if(paramsMap.size() == 0 || (paramsMap.size() == 1 && excludedCount == 1))
            throw new IllegalStateException("At least one param must be provided");
        if(excludedCount > 2)
            throw new IllegalStateException("A maximum of 2 *-excluded params are allowed");
        if(excludedCount == 0 && paramsMap.size() > 2)
            throw new IllegalStateException("Max 2 paramsMap are allowed if no *-excluded param is provided");


        //use AND query with 1 condition
        if(paramsMap.size() == 1) {
            if (paramsMap != null)
                parameters.put("paramsMap", paramsMap);

            if (variablesMap != null)
                parameters.put("variablesMap", variablesMap);

            QueryServicesClient queryServices = client.getServicesClient(QueryServicesClient.class);
            List<TaskInstance> taskWithDuplicates = queryServices.query(POT_OWNED_TASKS_BY_VARIABLES_AND_PARAMS, QUERY_MAP_TASK, "potOwnedTasksByVariablesAndParamsFilter", parameters, 0, ARBITRARY_LONG_VALUE, TaskInstance.class);
            List<Long> ids = taskWithDuplicates.stream().map(taskInstance -> taskInstance.getId()).distinct().collect(Collectors.toList());


            return ids;

        }

        //use OR query and apply additional filtering in java
        else {
            List<String> keysToCheck = new ArrayList<>(2);
            List<String> keysToQueries = new ArrayList<>(2);
            boolean multipleParamsToExclude = false;
            boolean multipleParamsToQuery = false;

            //there is no key to exclude
            if(keyToExclude == null) {
                keyToQuery = keys.get(0);

                if(keys.size() == 2)
                    keyToCheck = keys.get(1);
            }
            else {
	            /*
	              there is a key to exclude: 4 cases are allowed
	            */

                //1) -excluded and keyToQuery
                if(paramsMap.size() == 2) {
                    for (String key : keys) {
                        if (!key.equalsIgnoreCase(keyToExclude)) {
                            keyToQuery = key;
                            break;
                        }
                    }

                    keyToExcludeWithoutCheck = keyToExclude.replace("-excluded","");
                }

                //2) -excluded, keyToQuery and keyToCheck
                else if(paramsMap.size() == 3) {
                    for (String key : keys) {
                        if (key.matches(keyToExclude.replace("-excluded", ""))) {
                            keyToCheck = key;
                            break;
                        }
                    }

                    for (String key : keys) {
                        if (!key.equalsIgnoreCase(keyToCheck) && !key.equalsIgnoreCase(keyToExclude)) {
                            keyToQuery = key;
                            break;
                        }
                    }
                }
                //3) -excluded, 2 keyToQuery and keyToCheck
                else if(paramsMap.size() == 4) {
                    for (String key : keys) {
                        if (key.matches(keyToExclude.replace("-excluded", ""))) {
                            keyToCheck = key;
                        } else if(!key.contains("-excluded")){
                            keysToQueries.add(key);
                        }
                    }
                    keyToQuery = keysToQueries.get(0);
                    multipleParamsToQuery = true;

                }
                //4) 2 excluded, 1 keyToQuery and 2 keyToCheck
                else if(paramsMap.size() == 5) {
                    List<String> includedKeys = new ArrayList<>(3);
                    for(String key: keys) {
                        if (key.matches(".*-excluded"))
                            keysToCheck.add(key.replace("-excluded", ""));
                        else
                            includedKeys.add(key);
                    }
                    for(String key: includedKeys) {
                        if(!keysToCheck.contains(key))
                            keyToQuery = key;
                    }
                    multipleParamsToExclude = true;
                }

            }

            //set new paramsMap for query
            Map<String, List<String>> newParamsMap = new HashMap<>(1);
            newParamsMap.put(keyToQuery, paramsMap.get(keyToQuery));
            parameters.put("paramsMap", newParamsMap);

            if (variablesMap != null)
                parameters.put("variablesMap", variablesMap);

            QueryServicesClient queryServices = client.getServicesClient(QueryServicesClient.class);
            List<TaskInstance> taskWithDuplicates = queryServices.query(POT_OWNED_TASKS_BY_VARIABLES_AND_PARAMS, QUERY_MAP_TASK, "potOwnedTasksByVariablesAndParamsFilter", parameters, 0, ARBITRARY_LONG_VALUE, TaskInstance.class);
            List<Long> ids = taskWithDuplicates.stream().map(taskInstance -> taskInstance.getId()).distinct().collect(Collectors.toList());
            taskWithDuplicates = findTasksWithParameters(ids, true);

            //filter parameters for additional condition to check
            List<Long> idsToReturn = new ArrayList<>();
            for (Long id : ids) {
                for (TaskInstance t1 : taskWithDuplicates) {
                    if (t1.getId().equals(id)) {
                        if(!multipleParamsToExclude) {
                            if (keyToCheck != null) {
                                //extract second param
                                if (t1.getInputData().containsKey(keyToCheck)) {
                                    Object o1 = t1.getInputData().get(keyToCheck);
                                    if (paramsMap.get(keyToCheck).contains(o1))
                                        idsToReturn.add(id);
                                }
                                if (!t1.getInputData().containsKey(keyToCheck) && keyToExclude!= null)
                                    idsToReturn.add(id);
                            } else {
                                if (keyToExcludeWithoutCheck != null) {
                                    if (!t1.getInputData().containsKey(keyToExcludeWithoutCheck))
                                        idsToReturn.add(id);
                                }
                            }
                            if(multipleParamsToQuery) {
                                String keyTmp = keysToQueries.get(1);
                                if (t1.getInputData().containsKey(keyTmp)) {
                                    Object o1 = t1.getInputData().get(keyTmp);
                                    if (paramsMap.get(keyTmp).contains(o1))
                                        idsToReturn.add(id);
                                }
                            }
                        } else {
                            for(String incKey: keysToCheck) {
                                if (t1.getInputData().containsKey(incKey)) {
                                    Object o1 = t1.getInputData().get(incKey);
                                    if (paramsMap.get(incKey).contains(o1))
                                        idsToReturn.add(id);
                                }
                                if (!t1.getInputData().containsKey(incKey) && keyToExclude!= null)
                                    idsToReturn.add(id);
                            }
                        }
                    }
                }
            }

            return idsToReturn;
        }
    }

    public List<Long> potOwnedTasksByVariablesAndParams(String user, List<String> groups,
                                                        Map<String, List<String>> paramsMap, Map<String, List<String>> variablesMap) {

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("user", user);
        parameters.put("groups", groups);

        parameters.put("status", Arrays.asList(OPEN_STATUS));

        if (paramsMap != null) {
            parameters.put("paramsMap", paramsMap);
        }

        if (variablesMap != null) {
            parameters.put("variablesMap", variablesMap);
        }

        QueryServicesClient queryServices = client.getServicesClient(QueryServicesClient.class);

        List<TaskInstance> taskWithDuplicates = queryServices.query(POT_OWNED_TASKS_BY_VARIABLES_AND_PARAMS,
                QUERY_MAP_TASK, "potOwnedTasksByVariablesAndParamsFilter", parameters, 0, ARBITRARY_LONG_VALUE,
                TaskInstance.class);

        List<Long> ids = taskWithDuplicates.stream().map(taskInstance -> taskInstance.getId()).distinct()
                .collect(Collectors.toList());

        return ids;

    }

    public List<ProcessInstance> findProcessInstancesWithVariables(List<Long> processIds) {

        QueryFilterSpec queryFilterSpec = new QueryFilterSpecBuilder().in("processinstanceid", processIds).get();

        QueryServicesClient queryServices = client.getServicesClient(QueryServicesClient.class);
        List<ProcessInstance> processInstances = queryServices.query(FIND_PROCESS_INSTANCES_WITH_VARIABLES,
                QUERY_MAP_PI_WITH_VARS, queryFilterSpec, 0, ARBITRARY_LONG_VALUE, ProcessInstance.class);

        return processInstances;

    }


    public void signal(String containerId, String signalName, String event) {
        client.getServicesClient(ProcessServicesClient.class).signal(containerId, signalName, event);
    }

    public Long startProcess(String containerId, String processId) {
        HashMap<String, Object> parameters = new HashMap<>();
        return client.getServicesClient(ProcessServicesClient.class).startProcess(containerId, processId, parameters);
    }

    public Long startProcess(String containerId, BPMN bpmn, HashMap<String, Object> parameters) {
        return client.getServicesClient(ProcessServicesClient.class).startProcess(containerId, bpmn.value(), parameters);
    }

    public TaskInstance taskDetail(String containerId, Long taskId) {

        UserTaskServicesClient userTaskService = client.getServicesClient(UserTaskServicesClient.class);
        TaskInstance taskInstance = userTaskService.getTaskInstance(containerId, taskId, true, true, true);

        return taskInstance;

    }

    public List<TaskInstance> findTasksWithParameters(List<Long> taskIds, boolean asc) {
        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);
        QueryFilterSpec queryFilterSpec = new QueryFilterSpecBuilder()
                .in("taskid", taskIds)
                .oderBy("taskid", asc)
                .get();

        List<TaskInstance> taskInstances = queryService.query(AdvancedQueryFactory.FIND_TASKS_WITH_PARAMETERS,
                QUERY_MAP_TASK_WITH_VARS, queryFilterSpec, 0, ARBITRARY_LONG_VALUE, TaskInstance.class);

        return taskInstances;

    }

    public List<Long> findTasksByNameWithParameters(List<String> taskNames) {
        QueryServicesClient queryService = client.getServicesClient(QueryServicesClient.class);
        QueryFilterSpec queryFilterSpec = new QueryFilterSpecBuilder().in("name", taskNames)
                .in("status", Arrays.asList(OPEN_STATUS)).get();

        List<TaskInstance> taskInstances = queryService.query(AdvancedQueryFactory.FIND_TASKS_WITH_PARAMETERS, QUERY_MAP_TASK_WITH_VARS,
                queryFilterSpec, 0, ARBITRARY_LONG_VALUE, TaskInstance.class);
        List<Long> ids = taskInstances.stream().map(taskInstance -> taskInstance.getId()).distinct()
                .collect(Collectors.toList());

        return ids;

    }

    public void assignTaskToGroup(String containerId, Long taskId, String businessAdministrator, String group) {
        //TODO check if task is in CREATED status
        UserTaskServicesClient userTaskServicesClient = client.getServicesClient(UserTaskServicesClient.class);
        userTaskServicesClient.nominateTask(containerId, taskId, businessAdministrator, Arrays.asList(group));
    }

    public void assignTaskFromGroupToIndividual(String containerId, Long taskId, String group, String user) {
        UserTaskServicesClient userTaskServicesClient = client.getServicesClient(UserTaskServicesClient.class);
        TaskInstance taskInstance = userTaskServicesClient.findTaskById(taskId);
        if(taskInstance != null) {
            boolean idExists = taskInstance.getPotentialOwners().stream().anyMatch(t -> t.equals(group));
            if(idExists)
                userTaskServicesClient.claimTask(containerId, taskId, user);
        }

    }

    public Map<Long, List<TaskSummary>> getTasksByProcess(String containerId, String processId) {
        Map<Long, List<TaskSummary>> map = new HashMap<>();

        QueryServicesClient queryServicesClient = client.getServicesClient(QueryServicesClient.class);
        UserTaskServicesClient userTaskServicesClient = client.getServicesClient(UserTaskServicesClient.class);
        //TODO pagination via client

        /*
        process status
        int STATE_PENDING   = 0;
        int STATE_ACTIVE    = 1;
        int STATE_COMPLETED = 2;
        int STATE_ABORTED   = 3;
        int STATE_SUSPENDED = 4;
         */

        List<Integer> processStatus = Arrays.asList(0, 1);

        List<ProcessInstance> processInstances = queryServicesClient.findProcessInstancesByContainerId(containerId, processStatus, 0, 1000);
        if(processInstances != null && !processInstances.isEmpty()) {

            //TODO check if the status are OK
            List<String> taskStatus = Arrays.asList("Created", "Ready", "Reserved", "InProgress", "Suspended");

            for(ProcessInstance processInstance: processInstances) {
                //TODO pagination via client
                List<TaskSummary> taskSummaries = userTaskServicesClient.findTasksByStatusByProcessInstanceId(processInstance.getId(), taskStatus, 0, 1000);
                if(taskSummaries != null && !taskSummaries.isEmpty()) {
                    map.put(processInstance.getId(), taskSummaries);
                }
            }
        }

        return map;
    }

    public List<TaskInstance> findTasksWithParameters(QueryServicesClient queryService, List<Long> taskIds, boolean asc) {

        List<TaskInstance> taskInstances = null;
        QueryFilterSpec queryFilterSpec = null;

        if (taskIds.size() > 1000) {

            taskInstances = new ArrayList<>();
            int maxIndex = taskIds.size();
            int iMax = maxIndex / 1000;
            for (int i = 0; i < iMax; i++) {
                queryFilterSpec = new QueryFilterSpecBuilder().in(TASK_ID, taskIds.subList(1000 * i, 1000 * (i + 1)))
                        .oderBy(TASK_ID, asc).get();
                addTaskInstanceList(queryService.query(FIND_TASKS_WITH_PARAMETERS, QUERY_MAP_TASK_WITH_VARS,
                        queryFilterSpec, 0, ARBITRARY_LONG_VALUE, TaskInstance.class), taskInstances);
            }
            queryFilterSpec = new QueryFilterSpecBuilder().in(TASK_ID, taskIds.subList(1000 * iMax, maxIndex))
                    .oderBy(TASK_ID, asc).get();
            addTaskInstanceList(queryService.query(FIND_TASKS_WITH_PARAMETERS, QUERY_MAP_TASK_WITH_VARS,
                    queryFilterSpec, 0, ARBITRARY_LONG_VALUE, TaskInstance.class), taskInstances);

        } else {

            queryFilterSpec = new QueryFilterSpecBuilder().in(TASK_ID, taskIds).oderBy(TASK_ID, asc).get();
            taskInstances = queryService.query(FIND_TASKS_WITH_PARAMETERS, QUERY_MAP_TASK_WITH_VARS, queryFilterSpec,
                    0, ARBITRARY_LONG_VALUE, TaskInstance.class);

        }

        return taskInstances;
    }

    private void addTaskInstanceList(List<TaskInstance> fromList, List<TaskInstance> toList) {
        for (TaskInstance elem : fromList) {
            toList.add(elem);
        }
    }

    public List<ProcessInstance> findProcessInstancesWithVariables(QueryServicesClient queryService, List<Long> processIds) {

        List<ProcessInstance> processInstances = null;
        QueryFilterSpec queryFilterSpec = null;

        if (processIds.size() > 1000) {

            processInstances = new ArrayList<>();
            int maxIndex = processIds.size();
            int iMax = maxIndex / 1000;
            for (int i = 0; i < iMax; i++) {
                queryFilterSpec = new QueryFilterSpecBuilder()
                        .in(PROCESS_INSTANCE_ID, processIds.subList(1000 * i, 1000 * (i + 1))).get();
                addProcessInstanceList(queryService.query(FIND_PROCESS_INSTANCES_WITH_VARIABLES,
                        QUERY_MAP_PI_WITH_VARS, queryFilterSpec, 0, ARBITRARY_LONG_VALUE, ProcessInstance.class),
                        processInstances);
            }
            queryFilterSpec = new QueryFilterSpecBuilder()
                    .in(PROCESS_INSTANCE_ID, processIds.subList(1000 * iMax, maxIndex)).get();
            addProcessInstanceList(queryService.query(FIND_PROCESS_INSTANCES_WITH_VARIABLES, QUERY_MAP_PI_WITH_VARS,
                    queryFilterSpec, 0, ARBITRARY_LONG_VALUE, ProcessInstance.class), processInstances);

        } else {

            queryFilterSpec = new QueryFilterSpecBuilder().in(PROCESS_INSTANCE_ID, processIds).get();
            processInstances = queryService.query(FIND_PROCESS_INSTANCES_WITH_VARIABLES, QUERY_MAP_PI_WITH_VARS,
                    queryFilterSpec, 0, ARBITRARY_LONG_VALUE, ProcessInstance.class);

        }

        return processInstances;

    }

    private void addProcessInstanceList(List<ProcessInstance> fromList, List<ProcessInstance> toList) {
        for (ProcessInstance elem : fromList) {
            toList.add(elem);
        }
    }

    private List<TaskDetail> kieTaskDetailsToCommon(List<TaskInstance> taskInstances) {
        if ( taskInstances == null ) {
            return null;
        }

        List<TaskDetail> list = new ArrayList<TaskDetail>( taskInstances.size() );
        for ( TaskInstance taskInstance : taskInstances ) {
            list.add( kieToCommon( taskInstance ) );
        }

        return list;
    }

    private TaskDetail kieToCommon(TaskInstance kieTaskInstance) {
        if ( kieTaskInstance == null ) {
            return null;
        }

        TaskDetail taskDetail = new TaskDetail();

        taskDetail.setId( kieTaskInstance.getId() );
        taskDetail.setPriority( kieTaskInstance.getPriority() );
        taskDetail.setName( kieTaskInstance.getName() );
        taskDetail.setSubject( kieTaskInstance.getSubject() );
        taskDetail.setDescription( kieTaskInstance.getDescription() );
        taskDetail.setTaskType( kieTaskInstance.getTaskType() );
        taskDetail.setFormName( kieTaskInstance.getFormName() );
        taskDetail.setStatus( kieTaskInstance.getStatus() );
        taskDetail.setActualOwner( kieTaskInstance.getActualOwner() );
        taskDetail.setCreatedBy( kieTaskInstance.getCreatedBy() );
        taskDetail.setCreatedOn( kieTaskInstance.getCreatedOn() );
        taskDetail.setActivationTime( kieTaskInstance.getActivationTime() );
        taskDetail.setExpirationDate( kieTaskInstance.getExpirationDate() );
        taskDetail.setSkipable( kieTaskInstance.getSkipable() );
        taskDetail.setWorkItemId( kieTaskInstance.getWorkItemId() );
        taskDetail.setProcessInstanceId( kieTaskInstance.getProcessInstanceId() );
        taskDetail.setParentId( kieTaskInstance.getParentId() );
        taskDetail.setProcessId( kieTaskInstance.getProcessId() );
        taskDetail.setContainerId( kieTaskInstance.getContainerId() );
        List<String> list = kieTaskInstance.getPotentialOwners();
        if ( list != null ) {
            taskDetail.setPotentialOwners( new ArrayList<String>( list ) );
        }
        else {
            taskDetail.setPotentialOwners( null );
        }
        List<String> list1 = kieTaskInstance.getExcludedOwners();
        if ( list1 != null ) {
            taskDetail.setExcludedOwners( new ArrayList<String>( list1 ) );
        }
        else {
            taskDetail.setExcludedOwners( null );
        }
        List<String> list2 = kieTaskInstance.getBusinessAdmins();
        if ( list2 != null ) {
            taskDetail.setBusinessAdmins( new ArrayList<String>( list2 ) );
        }
        else {
            taskDetail.setBusinessAdmins( null );
        }
        Map<String, Object> map = kieTaskInstance.getInputData();
        if ( map != null ) {
            taskDetail.setInputData( new HashMap<String, Object>( map ) );
        }
        else {
            taskDetail.setInputData( null );
        }
        Map<String, Object> map1 = kieTaskInstance.getOutputData();
        if ( map1 != null ) {
            taskDetail.setOutputData( new HashMap<String, Object>( map1 ) );
        }
        else {
            taskDetail.setOutputData( null );
        }

        return taskDetail;
    }

}
