package com.su.activiti.controller;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/leave")
public class LeaveController {
    Logger logger = LoggerFactory.getLogger(LeaveController.class);

    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private HistoryService historyService;

    /**
     * 开始流程
     * @param jobNumber
     * @return
     * http://localhost:8080/leave/start?jobNumber=A1001
     */
    @RequestMapping("/start")
    public String start(String jobNumber) {
        //设置流程的发起者
        Authentication.setAuthenticatedUserId(jobNumber);
        //bpmn中定义process的id
        String instanceKey = "leaveProcess";
        logger.info("开始请假流程...");
        //设置流程参数，开启流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("jobNumber", jobNumber);
        //使用流程定义的key启动流程实例，key对应bpmn文件中id的属性值，使用key值启动，默认按照最新版本的流程定义启动
        //流程启动成功之后，获取到ProcessInstance信息
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(instanceKey, variables);
        logger.info("流程实例ID：" + instance.getId());
        logger.info("流程定义ID：" + instance.getProcessDefinitionId());
        //验证是否启动成功
        //通过查询正在运行的流程实例来判断
        ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery();
        //根据流程实例ID来查询
        List<ProcessInstance> runningList = processInstanceQuery.processInstanceId(instance.getProcessInstanceId()).list();
        logger.info("根据流程ID查询条数：" + runningList.size());
        //返回流程id
        return instance.getId();
    }

    /**
     * 查看任务
     * @param jobNumber
     * @return
     * http://localhost:8080/leave/showTask?jobNumber=A1001
     */
    @RequestMapping("/showTask")
    public List<Map<String, String>> showTask(String jobNumber) {
        //获取请求参数
        TaskQuery taskQuery = taskService.createTaskQuery();
        List<Task> taskList = null;
        if (jobNumber == null) {
            //获取所有人的任务
            taskList = taskQuery.list();
        } else {
            //获取分配人的任务
            taskList = taskQuery.taskAssignee(jobNumber).list();
        }
        if (taskList == null || taskList.size() == 0) {
            logger.info("查询任务列表为空！");
            return null;
        }
        //查询所有任务并封装
        List<Map<String, String>> resultList = new ArrayList<>();
        for (Task task : taskList) {
            Map<String, String> map = new HashMap<>();
            map.put("taskId", task.getId());
            map.put("name", task.getName());
            map.put("createTime", task.getCreateTime().toString());
            map.put("instanceId", task.getProcessInstanceId());
            map.put("assignee", task.getAssignee());
            map.put("executionId", task.getExecutionId());
            map.put("definitionId", task.getProcessDefinitionId());
            resultList.add(map);
        }
        return resultList;
    }

    /**
     * 员工提交申请
     * @param request
     * @return
     * http://127.0.0.1:8080/leave/employeeApply?taskId=81292e32-323f-11eb-bf9d-005056c00001&deptJobNumber=A1002&leaveDays=2&leaveReason=家里有事
     */
    @RequestMapping("/employeeApply")
    public String employeeApply(HttpServletRequest request) {
        logger.info("提交申请单信息...");
        //获取请求参数
        String taskId = request.getParameter("taskId");
        String deptJobNumber = request.getParameter("deptJobNumber");
        String leaveDays = request.getParameter("leaveDays");
        String leaveReason = request.getParameter("leaveReason");
        //查询任务
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            logger.info("任务ID：" + taskId + " 查询到任务为空！");
            return "fail";
        }
        //参数传递并提交申请
        Map<String, Object> variables = new HashMap<>();
        variables.put("days", leaveDays);
        variables.put("date", new Date());
        variables.put("reason", leaveReason);
        variables.put("deptJobNumber", deptJobNumber);
        taskService.complete(task.getId(), variables);
        logger.info("执行【员工申请】环节，流程推动到【部门审核】环节");
        return "success";
    }

    /**
     * 部门经历审核
     * @param request
     * @return
     */
    @RequestMapping("/deptManagerAudit")
    public String deptManagerAudit(HttpServletRequest request) {
        //获取请求参数
        //任务id
        String taskId = request.getParameter("taskId");
        //审批意见
        String auditOpinion = request.getParameter("auditOpinion");
        //审批结果：同意：1 不同意：0
        String audit = request.getParameter("audit");
        if (StringUtils.isBlank(taskId)) {
            return "fail";
        }
        //查找任务
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            logger.info("审核任务ID：" + taskId + " 查询到任务为空！");
            return "fail";
        }
        //设置局部变量参数，完成任务
        Map<String, Object> map = new HashMap<>();
        map.put("audit", audit);
        map.put("auditOpinion", auditOpinion);
        taskService.complete(taskId, map);
        return "success";
    }

    /**
     * 查看请假记录
     * @param jobNumber
     * @return
     */
    @RequestMapping("/historyList")
    public List<Map<String, Object>> historyList(String jobNumber) {
        List<HistoricProcessInstance> historicProcessInstances =
                historyService
                        .createHistoricProcessInstanceQuery()
                        .processDefinitionKey("leaveProcess")
                        .finished().startedBy(jobNumber)
                        .orderByProcessInstanceEndTime().desc()
                        .list();
        List<Map<String, Object>> list = new ArrayList<>();
        for (HistoricProcessInstance hpi : historicProcessInstances) {
            Map<String, Object> map = new HashMap<>();
            map.put("startUserId", hpi.getStartUserId());
            map.put("startTime", hpi.getStartTime());
            map.put("endTime", hpi.getEndTime());
            list.add(map);
            //查询审批结果
            Map<String, Object> variableMap = new HashMap<>();
            List<HistoricVariableInstance> variableInstanceList =
                    historyService
                            .createHistoricVariableInstanceQuery()
                            .processInstanceId(hpi.getId()).list();
            for (HistoricVariableInstance hvi : variableInstanceList) {
                variableMap.put(hvi.getVariableName(), hvi.getValue());
            }
            map.put("variables", variableMap);
            list.add(map);
        }
        return list;
    }

}
