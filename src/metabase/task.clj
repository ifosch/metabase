(ns metabase.task
  "Background task scheduling via Quartzite.  Individual tasks are defined in `metabase.task.*`.

   ## Regarding Task Initialization:

   The most appropriate way to initialize tasks in any `metabase.task.*` namespace is to implement the
   `task-init` function which accepts zero arguments.  This function is dynamically resolved and called
   exactly once when the application goes through normal startup procedures.  Inside this function you
   can do any work needed and add your task to the scheduler as usual via `schedule-task!`."
  (:require clojure.java.classpath
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.find :as ns-find]
            [clojurewerkz.quartzite.scheduler :as qs]))


(defonce ^:private quartz-scheduler
  (atom nil))

(defn- find-and-load-tasks
  "Search Classpath for namespaces that start with `metabase.tasks.`, then `require` them so initialization can happen."
  []
  (->> (ns-find/find-namespaces (clojure.java.classpath/classpath))
       (filter (fn [ns-symb]
                 (re-find #"^metabase\.task\." (name ns-symb))))
       set
       (map (fn [events-ns]
              (log/info "\tloading tasks namespace: " events-ns)
              (require events-ns)
              ;; look for `task-init` function in the namespace and call it if it exists
              (when-let [init-fn (ns-resolve events-ns 'task-init)]
                (init-fn))))
       dorun))

(defn start-scheduler!
  "Start our Quartzite scheduler which allows jobs to be submitted and triggers to begin executing."
  []
  (when-not @quartz-scheduler
    (log/debug "Starting Quartz Scheduler")
    ;; keep a reference to our scheduler
    (reset! quartz-scheduler (-> (qs/initialize) qs/start))
    ;; look for job/trigger definitions
    (find-and-load-tasks)))

(defn stop-scheduler!
  "Stop our Quartzite scheduler and shutdown any running executions."
  []
  (log/debug "Stopping Quartz Scheduler")
  ;; tell quartz to stop everything
  (when @quartz-scheduler
    (qs/shutdown @quartz-scheduler))
  ;; reset our scheduler reference
  (reset! quartz-scheduler nil))


(defn schedule-task!
  "Add a given job and trigger to our scheduler."
  [job trigger]
  (when @quartz-scheduler
    (qs/schedule @quartz-scheduler job trigger)))
