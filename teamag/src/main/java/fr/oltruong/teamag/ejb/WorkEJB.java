package fr.oltruong.teamag.ejb;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.ejb.Stateless;
import javax.persistence.Query;

import org.apache.commons.collections.CollectionUtils;

import com.google.common.collect.Maps;

import fr.oltruong.teamag.entity.Member;
import fr.oltruong.teamag.entity.Task;
import fr.oltruong.teamag.entity.Work;
import fr.oltruong.teamag.exception.ExistingDataException;
import fr.oltruong.teamag.utils.CalendarUtils;

@Stateless
public class WorkEJB extends AbstractEJB {

    public Map<Task, List<Work>> findWorks(Member member, Calendar month) {

        Map<Task, List<Work>> worksByTask = Maps.newHashMap();

        Query query = getEntityManager().createNamedQuery("findWorksByMember");
        query.setParameter("fmemberName", member.getName());
        query.setParameter("fmonth", month);

        @SuppressWarnings("unchecked")
        List<Work> listWorks = query.getResultList();

        if (CollectionUtils.isEmpty(listWorks)) {

            getLogger().debug("Creating new works for member " + member.getName());
            listWorks = createWorks(member, month);
        }

        worksByTask = transformWorkList(listWorks);

        return worksByTask;
    }

    @SuppressWarnings("unchecked")
    public List<Task> findAllTasks() {
        return getEntityManager().createNamedQuery("findAllTasks").getResultList();
    }

    public int getSumWorks(Member member, Calendar month) {
        Query query = getEntityManager().createNamedQuery("countWorksMemberMonth");
        query.setParameter("fmemberId", member.getId());
        query.setParameter("fmonth", month);
        Number sumOfPrice = (Number) query.getSingleResult();
        getLogger().debug("total " + sumOfPrice);
        return sumOfPrice.intValue();
    }

    private Map<Task, List<Work>> transformWorkList(List<Work> listWorks) {

        Map<Task, List<Work>> worksByTask = Maps.newHashMap();
        if (listWorks != null) {
            for (Work work : listWorks) {
                if (!worksByTask.containsKey(work.getTask())) {
                    worksByTask.put(work.getTask(), new ArrayList<Work>());
                }
                worksByTask.get(work.getTask()).add(work);
            }

        }

        return worksByTask;
    }

    private List<Work> createWorks(Member member, Calendar month) {

        List<Work> workList = null;

        List<Task> taskList = findMemberTasks(member);
        if (CollectionUtils.isNotEmpty(taskList)) {

            List<Calendar> workingDays = CalendarUtils.getWorkingDays(month);

            workList = new ArrayList<Work>(taskList.size() * workingDays.size());
            for (Task task : taskList) {
                for (Calendar day : workingDays) {
                    Work work = new Work();
                    work.setDay(day);
                    work.setMember(member);
                    work.setMonth(month);
                    work.setTask(task);

                    getEntityManager().persist(work);

                    workList.add(work);
                }
            }

        } else {
            getLogger().debug("Aucune activite");
        }
        return workList;

    }

    public void removeTask(Task task, Member member, Calendar month) {
        Query query = getEntityManager().createNamedQuery("deleteWorksByMemberTaskMonth");
        query.setParameter("fmemberId", member.getId());
        query.setParameter("ftaskId", task.getId());
        query.setParameter("fmonth", month);

        int rowsNumberDeleted = query.executeUpdate();

        getLogger().debug("Works supprim�s : " + rowsNumberDeleted);

        // Suppression pour la t�che de l'utilisateur

        Task taskDb = getEntityManager().find(Task.class, task.getId());

        Member memberDb = getEntityManager().find(Member.class, member.getId());

        taskDb.getMembers().remove(memberDb);

        if (taskDb.getMembers().isEmpty() && taskHasNoWorks(taskDb)) {
            getLogger().debug("La t�che n'a aucun objet attach� dessus. Suppression de la t�che");
            getEntityManager().remove(taskDb);
        } else {
            getLogger().debug("Mise � jour de la t�che");
            getEntityManager().persist(taskDb);
        }
    }

    private boolean taskHasNoWorks(Task taskDb) {

        Query query = getEntityManager().createNamedQuery("countWorksTask");
        query.setParameter("fTaskId", taskDb.getId());
        int total = ((Number) query.getSingleResult()).intValue();
        return total == 0;
    }

    public List<Task> findMemberTasks(Member member) {
        Query query = getEntityManager().createNamedQuery("findAllTasks");

        @SuppressWarnings("unchecked")
        List<Task> allTaskList = query.getResultList();

        List<Task> taskList = new ArrayList<Task>();

        for (Task task : allTaskList) {
            getLogger().debug("tache " + task.getId());

            if (task.getMembers() != null && !task.getMembers().isEmpty()) {

                getLogger().debug("tache Name" + task.getMembers().get(0).getName());
                getLogger().debug("tache Id" + task.getMembers().get(0).getId());
                getLogger().debug("Member id" + member.getId());

                if (task.getMembers().contains(member)) {
                    getLogger().debug("la t�che a bien comme member " + member.getName());
                    taskList.add(task);
                }
            }
        }

        return taskList;
    }

    public void createTask(Calendar month, Member member, Task task) throws ExistingDataException {
        Query query = getEntityManager().createNamedQuery("findTaskByName");
        query.setParameter("fname", task.getName());
        query.setParameter("fproject", task.getProject());

        Task taskDB = null;
        @SuppressWarnings("unchecked")
        List<Task> allTaskList = query.getResultList();

        if (CollectionUtils.isNotEmpty(allTaskList)) {
            getLogger().debug("La t�che existe d�j�");
            Task myTask = allTaskList.get(0);
            if (myTask.getMembers().contains(member)) {
                getLogger().debug("D�j� affect�e � la personne");
                throw new ExistingDataException();
            } else {
                getLogger().debug("Affectation � la personne " + member.getId());
                myTask.addMember(member);
                getEntityManager().merge(myTask);
                taskDB = myTask;
            }
        } else

        {
            getLogger().debug("Cr�ation d'une nouvelle t�che");

            // Reset task ID
            task.setId(null);
            task.addMember(member);
            getEntityManager().persist(task);
            taskDB = task;

        }

        getEntityManager().flush();

        // Cr�ation des objets Work
        getLogger().debug("Cr�ation des objets WORK");
        List<Calendar> workingDayList = CalendarUtils.getWorkingDays(month);

        for (Calendar day : workingDayList) {
            Work work = new Work();
            work.setDay(day);
            work.setMember(member);
            work.setMonth(month);
            work.setTask(taskDB);

            getEntityManager().persist(work);
        }

    }

    public void updateWorks(List<Work> workList) {
        for (Work work : workList) {
            work.setTotal(work.getTotalEdit());
            getEntityManager().merge(work);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Work> getWorksMonth(Calendar month) {
        Query query = getEntityManager().createNamedQuery("findWorksMonth");
        query.setParameter("fmonth", month);

        return query.getResultList();
    }

}
