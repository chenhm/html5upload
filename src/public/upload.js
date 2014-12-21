var r = new Resumable({
    target: '/',
    testChunks: false
});

r.assignDrop(document.getElementById('drop_zone'));

r.on('fileAdded', function(file, event) {
    //console.log(file)
    r.upload();
    handleFile(file);
});

r.on('fileError', function(file, message) {
    console.log(file)
    console.log(message)
});

// Setup the dnd listeners.
var dropZone = document.getElementById('drop_zone');
dropZone.addEventListener('dragover', handleDragOver, false);
dropZone.addEventListener('drop', handleFileSelect, false);
dropZone.addEventListener('dragenter', handleDragEnter, false);
dropZone.addEventListener('dragleave', handleDragLeave, false);

function handleDragEnter(e) {
    // this / e.target is the current hover target.
    this.classList.add('over');
    this.innerText = 'Drop to upload'
}

function handleDragLeave(e) {
    this.classList.remove('over'); // this / e.target is previous target element.
    this.innerText = 'Drop files here'
}

var uploadList = document.querySelector('#list ul') || document.getElementById('list').appendChild(document.createElement('ul'));

function handleFileSelect(evt) {
    evt.stopPropagation();
    evt.preventDefault();
    evt.target.classList.remove('over');
    evt.target.innerText = 'Drop files here';

    var files = evt.dataTransfer.files; // FileList object.

    // files is a FileList of File objects. List some properties.


//    for (var i = 0, f; f = files[i]; i++) {
//        handleFile(f);
//    }
    //document.getElementById('list').innerHTML = '<ul>' + output.join('') + '</ul>';
}

function handleDragOver(evt) {
    evt.stopPropagation();
    evt.preventDefault();
    evt.dataTransfer.dropEffect = 'copy'; // Explicitly show this is a copy.
}


function errorHandler(evt) {
    switch (evt.target.error.code) {
        case evt.target.error.NOT_FOUND_ERR:
            alert('File Not Found!');
            break;
        case evt.target.error.NOT_READABLE_ERR:
            alert('File is not readable');
            break;
        case evt.target.error.ABORT_ERR:
            break; // noop
        default:
            alert('An error occurred reading this file.');
    };
}



function handleFile(file) {
    // Reset progress indicator on new file selection.

    var output = [];
    var li = document.createElement('li');
    console.log(file)
    output.push('<strong>', escape(file.file.name), '</strong> - ',
        file.file.size, ' bytes');
    li.innerHTML = output.join('');

    uploadList.appendChild(li);


    var btn = document.createElement('button');
    btn.addEventListener('click', function() {
        r.cancel();
    })
    btn.innerText = 'Cancel upload';
    li.appendChild(btn);
    var progress_bar = document.createElement('div');
    progress_bar.classList.add('progress_bar');
    li.appendChild(progress_bar);

    var progress = progress_bar.appendChild(document.createElement('div'));
    progress.className = "percent";
    progress.style.width = '0%';
    progress.textContent = '0%';

    progress.classList.add('loading');

    r.on('fileSuccess', function(e) {

        // Ensure that the progress bar displays 100% at the end.
        progress.style.width = '100%';
        progress.textContent = '100%';
        setTimeout(function(){
            window.location.reload()
        },3000)
        
    })
    
    r.on('progress', function(){

        var percentLoaded = Math.round(r.progress() * 100);
        // Increase the progress bar length.
        if (percentLoaded < 100) {
            progress.style.width = percentLoaded + '%';
            progress.textContent = percentLoaded + '%';
        }
    })

}