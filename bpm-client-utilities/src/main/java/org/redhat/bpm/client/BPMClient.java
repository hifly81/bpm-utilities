package org.redhat.bpm.client;

import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.TaskInstance;
import org.redhat.bpm.model.TaskDetailWithVariable;
import org.redhat.bpm.model.TaskDetailWithVariableCustom;
import org.redhat.bpm.service.GatewaySettings;
import org.redhat.bpm.service.KieService;
import org.redhat.bpm.service.KieServiceFactory;
import org.redhat.bpm.util.BPMN;
import org.redhat.bpm.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BPMClient {

    private static final Logger LOG = LoggerFactory.getLogger(BPMClient.class);

    public static void main(String[] args) {

        LOG.info("JBPM external client demo\n  --> Choose a (Rest) or (JMS) client:");

        Scanner scanner = new Scanner(System.in);

        String clientMode = scanner.nextLine();

        LOG.info("Insert a Container ID:");

        String containerId = scanner.nextLine();

        KieService kieService = KieServiceFactory.create(clientMode, GatewaySettings.create(Constants.KIESERVER_USERNAME, Constants.KIESERVER_PASSWORD));
        kieService.startConverasation();
        //register advanced queries
        kieService.registerAdvancedQueries();

        while (true) {
            LOG.info("\nMenu Options\n");
            LOG.info("(1) - start processes");
            LOG.info("(2) - signal processes with an event");
            LOG.info("(3) - query - task detail");
            LOG.info("(4) - query - process variables");
            LOG.info("(5) - query - owned task by process variables and task variables");
            LOG.info("(6) - query - owned task by process variables and task variables - AND clause");
            LOG.info("(7) - query - owned task by process variables - AND clause");
            LOG.info("(8) - query - task by process variables and task names - AND clause");
            LOG.info("(9) - query - task by process variables and task names");
            LOG.info("(10) - query - task by groups,by process variables and task variables - not belonging to user");
            LOG.info("(11) - query - task by variables, params with variables and params");
            LOG.info("(12) - quit");

            LOG.info("Please enter your selection:\t");
            int selection = scanner.nextInt();
            scanner.nextLine();

            if (selection == 1) {

                LOG.info("Enter a list of processes instance to start, separeted with a comma" +
                        "(1) for process-signaling,\n " +
                        "(2) for process-signaling-bis,\n " +
                        "(3) for process-signaling-multiple-events,\n " +
                        "(4) for process-signaling-topic-pub,\n " +
                        "(5) for process-signaling-multiple-events-sub,\n " +
                        "(6) for process-test-task\n" +
                        "(7) insert a process-id definition\n");
                String processesNames = scanner.nextLine();
                StringTokenizer st = new StringTokenizer(processesNames, ",");
                while (st.hasMoreElements()) {
                    Integer index = Integer.valueOf((String)st.nextElement());
                    BPMN bpmn = BPMN.fromIndex(index);
                    Long pid = -1l;
                    if(bpmn == BPMN.PROCESS_SIGNALING_TOPIC_PUB) {
                        HashMap<String, Object> parameters = new HashMap<>();
                        parameters.put("destinationName", "topic/bpmnProcessesTopic");
                        parameters.put("containerId", Constants.CONTAINER_ID);
                        parameters.put("eventPublished", "task1_completed");
                        parameters.put("jmsConnection", "local");
                        pid = kieService.startProcess(containerId, bpmn, parameters);
                    }
                    else if(bpmn == BPMN.PROCESS_GENERIC) {
                        LOG.info("Enter a process definition id:");
                        String processDefinitionId = scanner.nextLine();
                        pid = kieService.startProcess(containerId, processDefinitionId);
                    }
                    else
                        pid = kieService.startProcess(containerId, bpmn.value());

                    LOG.info("Process instance {} started with {}", bpmn.value(), pid);
                }


            }
            else if (selection == 2) {
                LOG.info("Enter an event name: (example: task1_completed, event1, event2, event_sub)");
                String signalName = scanner.nextLine();
                kieService.signal(containerId, signalName,"OK");
            }
            else if (selection == 3) {
                LOG.info("Enter a task id (long value):");
                String taskId = scanner.nextLine();
                TaskInstance taskInstance = kieService.taskDetail(containerId, Long.valueOf(taskId));
                LOG.info("TaskInstance detail: {}", taskInstance);
            }
            else if (selection == 4) {
                LOG.info("Enter a process definition id:");
                String processDefinitionId = scanner.nextLine();
                List<ProcessInstance> processInstanceList = kieService.findProcessInstancesWithVariables(processDefinitionId, 0, 100);
                LOG.info("Process Instances list: {}", processInstanceList);
            }
            else if (selection == 5) {
                LOG.info("Enter a user:");
                String user = scanner.nextLine();
                LOG.info("Enter a list of group, separated with a comma:");
                String groupList = scanner.nextLine();
                StringTokenizer st = new StringTokenizer(groupList, ",");
                List<String> groups = new ArrayList<>();
                while (st.hasMoreElements()) {
                    groups.add(st.nextToken());
                }

                Map<String, List<String>> paramsMap = new HashMap<>();
                List<String> paramsValue = new ArrayList<> ();
                paramsValue.add("test");
                paramsMap.put("company", paramsValue);
                List<String> paramsValue2 = new ArrayList<> ();
                paramsValue2.add("livello1");
                paramsMap.put("level", paramsValue2);
                Map<String, List<String>> variablesMap = new HashMap<>();
                List<String> variablesValue1 = new ArrayList<String>();
                variablesValue1.add("test");
                variablesMap.put("company", variablesValue1);

                List<Long> tasks = kieService.potOwnedTasksByVariablesAndTaskParamsInOr(user, groups, paramsMap, variablesMap);
                LOG.info("Task list: {}", tasks);
            }
            else if (selection == 6) {
                LOG.info("Enter a user:");
                String user = scanner.nextLine();
                LOG.info("Enter a list of group, separated with a comma:");
                String groupList = scanner.nextLine();
                StringTokenizer st = new StringTokenizer(groupList, ",");
                List<String> groups = new ArrayList<>();
                while (st.hasMoreElements()) {
                    groups.add(st.nextToken());
                }

                Map<String, List<String>> paramsMap = new HashMap<>();
                //paramsMap.put("inParam1", Arrays.asList("Param1Value1"));
                //paramsMap.put("inParam2", Arrays.asList("Param2Value1"));


                paramsMap.put("inParam2", Arrays.asList("Param2Value1"));
                paramsMap.put("inParam1", Arrays.asList("Param1Value1"));


                Map<String, List<String>> variablesMap = new HashMap<>();
                variablesMap.put("stringProcessVar1", Arrays.asList("Var1ValuePag"));
                variablesMap.put("stringProcessVar2", Arrays.asList("Var2ValuePag"));

                List<Long> tasks = kieService.potOwnedTasksByVariablesAndTaskParamsInAnd(user, groups, paramsMap, variablesMap);
                LOG.info("Task list: {}", tasks);
            }
            else if (selection == 7) {

                LOG.info("Enter a user:");
                String user = scanner.nextLine();
                LOG.info("Enter a list of group, separated with a comma:");
                String groupList = scanner.nextLine();
                StringTokenizer st = new StringTokenizer(groupList, ",");
                List<String> groups = new ArrayList<>();
                while (st.hasMoreElements()) {
                    groups.add(st.nextToken());
                }


                Map<String, List<String>> variablesMap = new HashMap<>();
                List<String> variablesValue1 = new ArrayList<>();
                variablesValue1.add("test");
                List<String> variablesValue2 = new ArrayList<>();
                variablesValue2.add("dummy");

                variablesMap.put("company", variablesValue1);
                variablesMap.put("agency", variablesValue2);

                List<TaskDetailWithVariable> tasks = kieService.potOwnedTasksByVariablesWithVariablesAndParamsInAnd(user, groups, variablesMap);
                LOG.info("Task list: {}", tasks);
            }
            else if (selection == 8) {

                LOG.info("Enter a list of taskNames, separated with a comma:");
                String taskNamesList = scanner.nextLine();
                StringTokenizer st = new StringTokenizer(taskNamesList, ",");
                List<String> taskNames = new ArrayList<>();
                while (st.hasMoreElements()) {
                    taskNames.add(st.nextToken());
                }

                Map<String, List<String>> variablesMap = new HashMap<>();
                List<String> variablesValue1 = new ArrayList<>();
                variablesValue1.add("test");
                List<String> variablesValue2 = new ArrayList<>();
                variablesValue2.add("dummy");

                variablesMap.put("company", variablesValue1);
                variablesMap.put("agency", variablesValue2);

                List<TaskDetailWithVariable> tasks = kieService.tasksByVariablesAndNamesWithVariablesAndParamsInAnd(taskNames, variablesMap);
                LOG.info("Task list: {}", tasks);
            }
            else if (selection == 9) {

                LOG.info("Enter a list of taskNames, separated with a comma:");
                String taskNamesList = scanner.nextLine();
                StringTokenizer st = new StringTokenizer(taskNamesList, ",");
                List<String> taskNames = new ArrayList<>();
                while (st.hasMoreElements()) {
                    taskNames.add(st.nextToken());
                }

                Map<String, List<String>> variablesMap = new HashMap<>();
                List<String> variablesValue1 = new ArrayList<>();
                variablesValue1.add("test");

                variablesMap.put("company", variablesValue1);


                List<Long> tasks = kieService.tasksByNamesAndVariables(taskNames, variablesMap);
                LOG.info("Task list: {}", tasks);
            }
            else if (selection == 10) {
                LOG.info("Enter a user:");
                String user = scanner.nextLine();
                LOG.info("Enter a list of group, separated with a comma:");
                String groupList = scanner.nextLine();
                StringTokenizer st = new StringTokenizer(groupList, ",");
                List<String> groups = new ArrayList<>();
                while (st.hasMoreElements()) {
                    groups.add(st.nextToken());
                }

                Map<String, List<String>> paramsMap = new HashMap<>();
                List<String> paramsValue = new ArrayList<> ();
                paramsValue.add("test");
                paramsMap.put("company", paramsValue);
                List<String> paramsValue2 = new ArrayList<> ();
                paramsValue2.add("livello1");
                paramsMap.put("level", paramsValue2);
                Map<String, List<String>> variablesMap = new HashMap<>();
                List<String> variablesValue1 = new ArrayList<String>();
                variablesValue1.add("test");
                variablesMap.put("company", variablesValue1);

                List<Long> tasks = kieService.tasksByGroupsAndVariablesAndParamsNotActualOwner(groups, paramsMap, variablesMap, null, user);
                LOG.info("Task list: {}", tasks);
            }
            else if (selection == 11) {
                Map<String, List<String>> paramsMap = new HashMap<>();
                List<String> paramsValue = new ArrayList<> ();
                paramsValue.add("true");
                paramsMap.put("Skippable", paramsValue);
                Map<String, List<String>> variablesMap = new HashMap<>();
                List<String> variablesValue1 = new ArrayList<String>();
                variablesValue1.add("bpmsAdmin");
                variablesMap.put("initiator", variablesValue1);

                List<TaskDetailWithVariableCustom> tasks = kieService.tasksByVariablesAndParamsWithVariablesAndParams(paramsMap, variablesMap, true);
                LOG.info("Task list: {}", tasks);
            }
            else if (selection == 12) {
                kieService.endConversation();
                System.exit(1);
            }
        }

    }

}
