package fr.oltruong.teamag.backingbean;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.Instance;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import fr.oltruong.teamag.ejb.EmailEJB;
import fr.oltruong.teamag.ejb.MailBean;
import fr.oltruong.teamag.ejb.ParameterEJB;
import fr.oltruong.teamag.ejb.WorkEJB;
import fr.oltruong.teamag.entity.Member;
import fr.oltruong.teamag.entity.Task;
import fr.oltruong.teamag.entity.Work;
import fr.oltruong.teamag.exception.ExistingDataException;
import fr.oltruong.teamag.qualifier.UserLogin;
import fr.oltruong.teamag.utils.CalendarUtils;
import fr.oltruong.teamag.webbean.ColumnDayBean;
import fr.oltruong.teamag.webbean.RealizedFormWebBean;
import fr.oltruong.teamag.webbean.TaskWeekBean;

@SessionScoped
@ManagedBean
public class WorkController extends Controller {

    @Inject
    private Logger logger;

    @Inject
    @UserLogin
    private Instance<Member> memberInstance;

    @Inject
    private Task newTask;

    private Map<Task, List<Work>> works;

    @Inject
    private RealizedFormWebBean realizedBean;

    @Inject
    private WorkEJB workEJB;

    @Inject
    private EmailEJB mailEJB;

    @Inject
    private ParameterEJB parameterEJB;

    public String doCreateActivity() {

        this.logger.info("Adding a new activity");

        if (StringUtils.isBlank(this.newTask.getName())) {
            FacesMessage msg = null;
            msg = new FacesMessage(FacesMessage.SEVERITY_ERROR, getMessage("impossibleAdd"), getMessage("nameTask"));
            FacesContext.getCurrentInstance().addMessage(null, msg);

        } else {

            try {
                this.workEJB.createTask(this.realizedBean.getCurrentMonth(), getMember(), this.newTask);

                this.works = this.workEJB.findWorks(getMember(), CalendarUtils.getFirstDayOfMonth(Calendar.getInstance()));
                initTaskWeek();

                FacesMessage msg = null;
                msg = new FacesMessage(FacesMessage.SEVERITY_INFO, getMessage("createdTask"), "");
                FacesContext.getCurrentInstance().addMessage(null, msg);

            } catch (ExistingDataException e) {
                FacesMessage msg = null;
                msg = new FacesMessage(FacesMessage.SEVERITY_WARN, getMessage("existingTask"), getMessage("noChange"));
                FacesContext.getCurrentInstance().addMessage(null, msg);
            }
        }

        return "realized.xhtml";
    }

    public String deleteTask() {
        this.logger.info("Deleting task " + this.realizedBean.getSelectedTaskWeek().getTask().getName());

        this.workEJB.removeTask(this.realizedBean.getSelectedTaskWeek().getTask(), getMember(), this.realizedBean.getCurrentMonth());
        init();
        return "realized.xhtml";
    }

    public String previousWeek() {
        this.logger.debug("Click Previous week");
        this.realizedBean.decrementWeek();
        initTaskWeek();
        return "realized.xhtml";
    }

    public String nextWeek() {
        this.realizedBean.incrementWeek();
        initTaskWeek();
        return "realized.xhtml";
    }

    public String update() {

        List<Work> changedWorks = findChangedWorks(this.realizedBean.getTaskWeeks());
        this.workEJB.updateWorks(changedWorks);

        FacesMessage msg = null;
        if (changedWorks.isEmpty()) {
            msg = new FacesMessage(FacesMessage.SEVERITY_WARN, getMessage("noChangesDetected"), "");

        } else {
            msg = new FacesMessage(FacesMessage.SEVERITY_INFO, getMessage("updated"), "");
            sendNotification();
            initTaskWeek();
        }
        FacesContext.getCurrentInstance().addMessage(null, msg);

        return "realized.xhtml";
    }

    public List<String> completeProject(String query) {
        List<Task> tasks = this.workEJB.findAllTasks();

        List<String> results = Lists.newArrayListWithExpectedSize(tasks.size());
        if (!StringUtils.isBlank(query) && query.length() > 1) {

            for (Task task : tasks) {
                if (StringUtils.containsIgnoreCase(task.getProject(), query) && !results.contains(task.getProject())) {
                    results.add(task.getProject());
                }

            }
        }
        return results;
    }

    public List<String> completeName(String query) {
        List<Task> tasks = this.workEJB.findAllTasks();

        List<String> results = Lists.newArrayListWithExpectedSize(tasks.size());
        if (!StringUtils.isBlank(query) && query.length() > 1) {
            for (Task task : tasks) {
                // Do not propose task that the member already has
                if (!task.getMembers().contains(getMember()) && StringUtils.containsIgnoreCase(task.getName(), query) && !results.contains(task.getName())) {
                    results.add(task.getName());
                }

            }
        }
        return results;
    }

    private void sendNotification() {
        int total = this.workEJB.getSumWorks(getMember(), this.realizedBean.getCurrentMonth());

        int nbWorkingDays = CalendarUtils.getWorkingDays(this.realizedBean.getCurrentMonth()).size();
        if (total == nbWorkingDays) {

            MailBean email = buildEmail();
            this.mailEJB.sendEmail(email);
        }
    }

    private MailBean buildEmail() {
        MailBean email = new MailBean();
        email.setContent("Realise complet");
        email.setRecipient(this.parameterEJB.getAdministratorEmail());
        email.setSubject("Realise de " + getMember().getName());
        return email;
    }

    public String init() {
        // this.realizedBean = new RealizedFormWebBean();
        this.realizedBean.setDayCursor(Calendar.getInstance());

        Calendar firstDayOfMonth = CalendarUtils.getFirstDayOfMonth(Calendar.getInstance());
        this.realizedBean.setCurrentMonth(firstDayOfMonth);
        this.works = this.workEJB.findWorks(getMember(), firstDayOfMonth);

        initTaskWeek();
        return "realized";
    }

    private void initTaskWeek() {
        if (this.works != null) {
            Integer weekNumber = this.realizedBean.getWeekNumber();

            Map<String, ColumnDayBean> mapColumns = Maps.newHashMapWithExpectedSize(5);

            List<TaskWeekBean> taskWeekList = new ArrayList<TaskWeekBean>(this.works.keySet().size());
            for (Task task : this.works.keySet()) {
                TaskWeekBean taskWeek = new TaskWeekBean();
                taskWeek.setTask(task);
                for (Work work : this.works.get(task)) {

                    if (work.getDay().get(Calendar.WEEK_OF_YEAR) == weekNumber) {

                        ColumnDayBean columnDay = new ColumnDayBean();
                        columnDay.setDay(work.getDay());
                        taskWeek.addWork(columnDay.getDayNumber(), work);

                        if (mapColumns.get(work.getDayStr()) == null) {
                            columnDay.addTotal(work.getTotal());
                            mapColumns.put(work.getDayStr(), columnDay);
                        } else {
                            mapColumns.get(work.getDayStr()).addTotal(work.getTotal());
                        }

                    }
                }
                taskWeekList.add(taskWeek);

            }

            this.realizedBean.getColumnsDay().clear();
            for (ColumnDayBean col : mapColumns.values()) {
                this.realizedBean.addColumnDay(col);

            }
            Collections.sort(this.realizedBean.getColumnsDay());
            this.realizedBean.setTaskWeeks(taskWeekList);
            Collections.sort(this.realizedBean.getTaskWeeks());

        } else {
            logger.debug("No taskMonth found");
        }

    }

    private List<Work> findChangedWorks(List<TaskWeekBean> taskWeeks) {
        List<Work> worksChanged = Lists.newArrayList();
        for (TaskWeekBean taskWeek : taskWeeks) {
            for (Work work : taskWeek.getWorks()) {
                if (work.hasChanged()) {
                    worksChanged.add(work);
                }
            }
        }

        return worksChanged;
    }

    public RealizedFormWebBean getRealizedBean() {
        return this.realizedBean;
    }

    public void setRealizedBean(RealizedFormWebBean realizedBean) {
        this.realizedBean = realizedBean;
    }

    public Member getMember() {
        return this.memberInstance.get();
    }

    public Task getNewTask() {
        return this.newTask;
    }

    public void setNewTask(Task newActivity) {
        this.newTask = newActivity;
    }

}
