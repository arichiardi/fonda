<img src="https://www.elasticpath.com/sites/all/themes/bootstrap/images/elastlic-path-logo-RGB.svg" alt="elasticpath logo" title="elasticpath" align="right" width="150"/>

# Fonda

An async pipeline approach to functional core - imperative shell.

## Asynchronous pipeline of steps

Fonda sequentially executes an (a)synchronous series of steps, one after the other, augmenting a context map.

After the steps are run, the optional loggers are called.

After the loggers are called, the callbacks will be called.

Fonda distinguishes between exceptions and [anomalies](https://github.com/cognitect-labs/anomalies/blob/master/src/cognitect/anomalies.cljc) errors

Anomalies are just data that define a recoverable "business" error, as opposed to JavaScript js/Error or Java Exception, 
which never meant to be caught and are usually caused by programming bugs.
 
By default, Fonda wraps the anomalies in a map with the key `:cognitect.anomalies/anomaly`.

It is possible to create custom anomalies by passing an anomaly? function in the configuration.

## Syntax

```clojure
(execute config steps initial-ctx on-success on-anomaly on-exception)
```
- **config** A map with:

      - [opt] :anomaly?      A function that gets a map and determines if it is an anomaly
      - [opt] :log-exception A function gets called with the runtime context when there is an exception.
      - [opt] :log-anomaly   A function that gets called with te runtime context when a step returns an anomaly
      - [opt] :log-success   A function that gets called after all the steps succeeded
      
- **steps**: Each item on the steps collection must be either a Tap, or a Processor

      Tap:
       - :tap  A function that gets the context but doesn't augment it
       - :name The name of the step

      Processor:
       - :processor A function that gets the context returns a result that is assoced into the context on the given path
       - :path      Path where to assoc the result of the processor
       - :name      The name of the step
       
- **initial-ctx** The context data that gets passed to the steps functions. Must be a map
               
- **on-success**  Callback that gets called with the context if all steps succeeded
- **on-anomaly**   Callback that gets called with an anomaly when any step returns one
- **on-exception** Callback that gets called with an exception when any step triggers one

### Steps

- There are two types of steps: **taps** and **processors**.

- Processors get a path and a function. The data returned will be placed in the context on that path.

- Taps only get a function, and if they succeed, the result is ignored.

- If any step returns an anomaly, or triggers an exception, the execution of the steps stops and the global taps 
  callbacks will be called.

- If any step returns an anomaly, the log-anomaly will be called with the RuntimeContext, and then the on-anomaly callback

- If any step triggers an exception, the log-exception will be called with the RuntimeContext, and then on-exception callback.

#### Processor steps

Processors are maps with the following keys:

- **:processor** A function that gets a context map and returns data. Can be asynchronous.
               If it returns an anomaly, or it triggers an error, further steps execution will be short-circuited.
- **:path** A vector that determines where in the context will the resolve result be associated.
- **:name** A descriptive name for the step

#### Tap steps

Taps are maps with the following keys:

- **:tap**   A function that gets the context map. If it succeeds, the result is then ignored.
            It will still block the steps processing if it is asynchronous, and it will interrupt the steps execution
            if it returns an anomaly, or it triggers an exception
- **:name**  A descriptive name for the step

### Runtime context

Global taps are called with the runtime context. The runtime context is a map that contains, among other things, 
the context that is passed to each step. It also contains:

- **:step-log** A collection of step logs. By default only step names are logged.


## Examples

```clojure
(fonda/execute

    {

     ;; [opt] By default, clojure anomaly
     :anomaly?      (fn [x] (:my-weird-error x))

     ;; [opt]
     :log-exception (fn [{:keys [ctx exception step-log]}])

     :log-anomaly   (fn [{:keys [ctx anomaly step-log]}])
     
     :log-success   (fn [{:keys [ctx step-log]}])

     }

    [
     ;; Blocking tap, it short-circuits if it throws an exception
     {:tap          (fn [ctx])}

     {:processor    (fn [])
      :path         [:something]
      :name         "step-name"

      ;; Processor can return data, an anomaly, or throw an exception
      }
     ]

    ;; Initial context
    {}

    ;; success cb
    (fn [result])

    ;; anomaly cb
    (fn [anomaly])

    ;; exception cb
    (fn [exception])

    )
```

```clojure
(fonda/execute
  {:log-exception     (fn [{:keys [ctx error step-log]}] (js/console.log "Exception happened:" error))
   
   :log-anomaly       (fn [{:keys [ctx anomaly step-log]}] (js/console.log "An anomaly happened:" anomaly))
   
   :log-success       (fn [{:keys [ctx step-log]}] (js/console.log "Operation successful!"))
   }
  
  [
   ;; Processor that retrieves data remotely
   {:processor        (fn [ctx] (ajax/get "http://remote-thing-url"))
    :name             "get-remote-thing"
    :path             [:remote-thing] }
   
   ;; Processor that processes the data
   {:processor        (fn [{:keys [remote-thing]}] (process-remote-thing remote-thing))
    :name             "process-thing"
    :path             [:remote-thing-processed]}
   
   ;; Tap that logs things
   {:tap              (fn [{:keys [remote-thing]}] (js/console.log "remote thing:"))}
   ]

  ;; On complete
  (fn [{:keys [remote-thing-processed]}] 
   (call-on-success-cb remote-thing-processed))
   
  ;; On anomaly
  (fn [anomaly] 
   (call-on-anomaly-cb anomaly))
   
  ;; On error
  (fn [error] 
   (call-on-error-cb error)))

```