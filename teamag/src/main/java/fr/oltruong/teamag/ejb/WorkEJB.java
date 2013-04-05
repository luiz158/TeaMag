package fr.oltruong.teamag.ejb;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import fr.oltruong.teamag.entity.Member;
import fr.oltruong.teamag.entity.Task;
import fr.oltruong.teamag.entity.Work;
import fr.oltruong.teamag.exception.ExistingDataException;
import fr.oltruong.teamag.utils.CalendarUtils;

@Stateless
public class WorkEJB
{

    @PersistenceContext( unitName = "ejbPU" )
    private EntityManager em;

    public Map<Task, List<Work>> findWorks( Member member, Calendar month )
    {

        Map<Task, List<Work>> worksByTask = new HashMap<Task, List<Work>>();

        Query query = em.createNamedQuery( "findWorksByMember" );
        query.setParameter( "fmemberName", member.getName() );
        query.setParameter( "fmonth", month );

        @SuppressWarnings( "unchecked" )
        List<Work> listWorks = query.getResultList();

        if ( listWorks == null || listWorks.isEmpty() )
        {
            System.out.println( "Creating new works for member " + member.getName() );
            listWorks = createWorks( member, month );
        }

        worksByTask = transformWorkList( listWorks );

        return worksByTask;
    }

    @SuppressWarnings( "unchecked" )
    public List<Task> findAllTasks()
    {
        return em.createNamedQuery( "findAllTasks" ).getResultList();
    }

    public int getSumWorks( Member member, Calendar month )
    {
        Query query = em.createNamedQuery( "countWorksMemberMonth" );
        query.setParameter( "fmemberId", member.getId() );
        query.setParameter( "fmonth", month );
        Number sumOfPrice = (Number) query.getSingleResult();
        System.out.println( "total " + sumOfPrice );
        return sumOfPrice.intValue();
    }

    private Map<Task, List<Work>> transformWorkList( List<Work> listWorks )
    {

        Map<Task, List<Work>> worksByTask = new HashMap<Task, List<Work>>();
        if ( listWorks != null )
        {
            for ( Work work : listWorks )
            {
                if ( !worksByTask.containsKey( work.getTask() ) )
                {
                    worksByTask.put( work.getTask(), new ArrayList<Work>() );
                }
                worksByTask.get( work.getTask() ).add( work );
            }

        }

        return worksByTask;
    }

    private List<Work> createWorks( Member member, Calendar month )
    {

        List<Work> works = null;

        List<Task> tasks = findMemberTasks( member );
        if ( tasks != null && !tasks.isEmpty() )
        {

            List<Calendar> workingDays = CalendarUtils.getWorkingDays( month );

            works = new ArrayList<Work>( tasks.size() * workingDays.size() );
            for ( Task task : tasks )
            {
                for ( Calendar day : workingDays )
                {
                    Work work = new Work();
                    work.setDay( day );
                    work.setMember( member );
                    work.setMonth( month );
                    work.setTask( task );

                    em.persist( work );

                    works.add( work );
                }
            }

        }
        else
        {
            System.out.println( "Aucune activite" );
        }
        return works;

    }

    public void removeTask( Task task, Member member, Calendar month )
    {
        Query query = em.createNamedQuery( "deleteWorksByMemberTaskMonth" );
        query.setParameter( "fmemberId", member.getId() );
        query.setParameter( "ftaskId", task.getId() );
        query.setParameter( "fmonth", month );

        int rowsNumberDeleted = query.executeUpdate();

        System.out.println( "Works supprim�s : " + rowsNumberDeleted );

        // Suppression pour la t�che de l'utilisateur

        Task taskDb = em.find( Task.class, task.getId() );

        Member memberDb = em.find( Member.class, member.getId() );

        taskDb.getMembers().remove( memberDb );

        if ( taskDb.getMembers().isEmpty() && taskHasNoWorks( taskDb ) )
        {
            System.out.println( "La t�che n'a aucun objet attach� dessus. Suppression de la t�che" );
            em.remove( taskDb );
        }
        else
        {
            System.out.println( "Mise � jour de la t�che" );
            em.persist( taskDb );
        }
    }

    private boolean taskHasNoWorks( Task taskDb )
    {

        Query query = em.createNamedQuery( "countWorksTask" );
        query.setParameter( "fTaskId", taskDb.getId() );
        int total = ( (Number) query.getSingleResult() ).intValue();
        return total == 0;
    }

    public List<Task> findMemberTasks( Member member )
    {
        Query query = em.createNamedQuery( "findAllTasks" );

        @SuppressWarnings( "unchecked" )
        List<Task> allTasks = query.getResultList();

        List<Task> tasks = new ArrayList<Task>();

        for ( Task task : allTasks )
        {
            System.out.println( "tache " + task.getId() );

            if ( task.getMembers() != null && !task.getMembers().isEmpty() )
            {

                System.out.println( "tache Name" + task.getMembers().get( 0 ).getName() );
                System.out.println( "tache Id" + task.getMembers().get( 0 ).getId() );
                System.out.println( "Member id" + member.getId() );

                if ( task.getMembers().contains( member ) )
                {
                    System.out.println( "la t�che a bien comme member " + member.getName() );
                    tasks.add( task );
                }
            }
        }

        return tasks;
    }

    public void createTask( Calendar month, Member member, Task task )
        throws ExistingDataException
    {
        Query query = em.createNamedQuery( "findTaskByName" );
        query.setParameter( "fname", task.getName() );
        query.setParameter( "fproject", task.getProject() );

        Task taskDB = null;
        @SuppressWarnings( "unchecked" )
        List<Task> allTasks = query.getResultList();

        if ( allTasks != null && !allTasks.isEmpty() )
        {
            System.out.println( "La t�che existe d�j�" );
            // La t�che existe d�j�
            Task myTask = allTasks.get( 0 );
            if ( myTask.getMembers().contains( member ) )
            {
                System.out.println( "D�j� affect�e � la personne" );
                throw new ExistingDataException();
            }
            else
            {
                System.out.println( "Affectation � la personne " + member.getId() );
                myTask.addMember( member );
                em.merge( myTask );
                taskDB = myTask;
            }
        }
        else
        // Cr�ation de la t�che
        {
            System.out.println( "Cr�ation d'une nouvelle t�che" );

            // Reset task ID
            task.setId( null );
            task.addMember( member );
            em.persist( task );
            taskDB = task;

        }

        em.flush();

        // Cr�ation des objets Work
        System.out.println( "Cr�ation des objets WORK" );
        List<Calendar> workingDays = CalendarUtils.getWorkingDays( month );

        for ( Calendar day : workingDays )
        {
            Work work = new Work();
            work.setDay( day );
            work.setMember( member );
            work.setMonth( month );
            work.setTask( taskDB );

            em.persist( work );
        }

    }

    public void updateWorks( List<Work> works )
    {
        for ( Work work : works )
        {
            work.setTotal( work.getTotalEdit() );
            em.merge( work );
        }
    }

    @SuppressWarnings( "unchecked" )
    public List<Work> getWorksMonth( Calendar month )
    {
        Query query = em.createNamedQuery( "findWorksMonth" );
        query.setParameter( "fmonth", month );

        return query.getResultList();
    }

}
